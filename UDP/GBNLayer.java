import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class GBNLayer {

    private final DatagramSocket socket;

    private static final int TIMEOUT = 1000;
    private static final int BUFFER_SIZE = 256;
    private static final int WINDOW_SIZE = 4; // ikkunan koko > max 4 pakettia ennen kuin odotetan ack

    // lähettäjän
    private int base = 0; // vanhin lähetetty mutta ei vielä ackattu sekvenssinumero
    private int nextSeqNum = 0; // seuraava sekvenssinumero jolla paketti voidaan lähettää

    // vastaanottajan
    private int expectedSeq = 0; // odotettu sekvenssinumero tulevalle paketille
    private int lastAcked = -1; // viimeisin ackattu sekventtinumero

    private final Map<Integer, byte[]> sentPackets = new HashMap<>(); // tallennetaan lähetetyt paketit uudelleenlähetystä varten

    public GBNLayer(DatagramSocket socket) {
        this.socket = socket;
    }

    // lähettäjän funktio! 
    public void sendMessages(String[] messages, InetAddress address, int port) throws IOException {
        socket.setSoTimeout(TIMEOUT); // timeout ack odotukselle

        // mikä indeksi seuraavaksi lähetettävälle viestille -> 
        int messageIndex = 0;

        // jatketaan kunnes kaikki viestit on lähetetty ja ackattu
        while (base < messages.length) { 

            // seuraavan sekvenssinumero on ikkunann sisällä ja on vielä lähettämättömiä viestejä
            while (nextSeqNum < base + WINDOW_SIZE && messageIndex < messages.length) {
                byte[] packetBytes = makePacket(nextSeqNum, messages[messageIndex]);

        
                DatagramPacket packet = new DatagramPacket(
                        packetBytes,
                        packetBytes.length,
                        address,
                        port
                );

                socket.send(packet);

                sentPackets.put(nextSeqNum, packetBytes); // tallennetaan lähetetty paketti uudelleenlähetystä varten
                System.out.println("Sent data seq=" + nextSeqNum + ":" + messages[messageIndex]); 

                nextSeqNum++; // kasvatettaan sekventtssinumeroa ja lähetettyjen viestien indeksiä
                messageIndex++;
            }

            // ikkuna täynnä tai kaikki viestit lähetetty > odotettaan ack
            try {
                byte[] buffer = new byte[BUFFER_SIZE];
                DatagramPacket ackPacket = new DatagramPacket(buffer, buffer.length);

                // odotetaan ack
                socket.receive(ackPacket);

                int length = ackPacket.getLength();

                // tarkistetaan onko ack tyhjä tia onko tapahtunut bittivirhe
                if (isCorrupted(buffer, length)) {
                    System.out.println("Corrupted ACK > ignoring");
                    continue;
                }

                String text = getText(buffer, length);

                // tarkistetaan onko paketin dataosa ACK > onko kyseessä ACK paketti. jos ei, jatketaan odottamista
                if (!text.equals("ACK")) {
                    System.out.println("Non-ACK received >ignoring");
                    continue;
                }


                int ackSeq = getSeq(buffer);
                // kumulatiiviset ackit! jos ackataan esim seq 2 > kaikki seq 0,1 ja 2 on vastaanotettu onnistuneesti
                System.out.println("Received cumulative ACK seq=" + ackSeq);

                // jos ackattu sekvenssinumero on ikkunan alussa tai sen jälkeen, voidaan siirtää ikkunaa eteenpäin
                // eli base siirretään kohtaan ackseq +1 
                if (ackSeq >= base) {
                    base = ackSeq + 1;
                    System.out.println("Window base moved to" + base);
                }

            } catch (SocketTimeoutException e) { // timeout jos ackia ei saatu, läheteään uudelleen kaikki ikkunan paketit
                System.out.println("Timeout resending packets from seq " + base + " to " + (nextSeqNum - 1));

                for (int seq = base; seq < nextSeqNum; seq++) {
                    byte[] packetBytes = sentPackets.get(seq);

                    if (packetBytes != null) {
                        DatagramPacket packet = new DatagramPacket(
                                packetBytes,
                                packetBytes.length,
                                address,
                                port
                        );

                        socket.send(packet);
                        System.out.println("Resent DATA seq=" + seq);
                    }
            }
         }
        }

        // kaikki viestit on lähetetty ja ackattu, voidaan poistaa timeout
        socket.setSoTimeout(0);
        System.out.println("All messages sent ok");
    }

    // vastaanottajan funktio
    public String receive() throws IOException {
        while (true) {
            byte[] buffer = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            socket.receive(packet);

            int length = packet.getLength();

            // tarkistetaan onko paketti tyhjä tai onko tapahtunut bittivirhe
            if (isCorrupted(buffer, length)) {
                // jos on, läheteään ack viimeisimpään onnistuneesti vastaanotettuun pakettiin (lastAcked) > kumulatiivinen ack
                // aiemasta tehtävästä opittiin, että kun ack lähetetään viimesempään onnistuneeseen pakettiin toistamiseen, se toimii ikään kuin nackina, koska lähettäjä ei edennyt ikkunaa eteenpäin. 
                // lähettäjän tarvii siis lähettää uudestaan kaikki ackattujen ja ackattujen välillä olevat paketit
                System.out.println("Corrupted DATA, sending ACK for last in-order packet: " + lastAcked);
                sendAck(lastAcked, packet.getAddress(), packet.getPort());
                continue;
            }

            String text = getText(buffer, length);

            // tarkistetaan onko paketin dataosa ACK > jos on, jatketaan odottamista, koska vastaanottaja ei odota ACK paketteja
            if (text.equals("ACK")) {
                continue;
            }

            int seq = getSeq(buffer);

            // jos sekvenssinumero täsmää odotettuun, paketti on onnistuneesti vastaanotettu ja voidaan lähettää ack
            if (seq == expectedSeq) {
                System.out.println("Received expected DATA seq=" + seq + ": " + text);

                lastAcked = seq;
                expectedSeq++; // päivitellään myös sekvenssinumero

                sendAck(seq, packet.getAddress(), packet.getPort());

                return text;

            } else {
                System.out.println("Out-of-order DATA seq=" + seq + ", expected " + expectedSeq);
                System.out.println("Discarding packet and sending ACK " + lastAcked);

                sendAck(lastAcked, packet.getAddress(), packet.getPort());
            }
        }
    }

    // metodi ackin lähettämiseen
    private void sendAck(int seq, InetAddress address, int port) throws IOException {
       // jos sekvenssinumer on pienempi kuin 0 > vastaanottaja ei ole vielä vastaanottanut yhtään onnistunutta pakettia > ei voida lähettää ackia, koska ackataan aina viimeisimpään onnistuneeseen pakettiin
        if (seq < 0) {
            System.out.println("No packet accepted yet > no ACK sent");
            return;
        }

        byte[] ackBytes = makePacket(seq, "ACK");

        DatagramPacket ack = new DatagramPacket(
                ackBytes,
                ackBytes.length,
                address,
                port
        );

        socket.send(ack);
        System.out.println("Sent ACK seq=" + seq);
    }

    // apufunktio paketin luomiseen sekvenssinumerolla, tekstillä ja crc:llä
    private static byte[] makePacket(int seq, String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);

        byte[] packet = new byte[1 + data.length + 1];

        packet[0] = (byte) seq;
        System.arraycopy(data, 0, packet, 1, data.length);

        byte crc = CRC8.calculate(packet, packet.length - 1); //crc tavu paketin viimeiseksi
        packet[packet.length - 1] = crc;

        return packet;
    }

    // apufunktio paketin tarkistamiseen > onko tyhjä tai onko tapahtunut bittivirhe
    private static boolean isCorrupted(byte[] packet, int length) {
        if (length < 2) {
            return true;
        }

        byte receivedCrc = packet[length - 1];
        byte calculatedCrc = CRC8.calculate(packet, length - 1);

        return receivedCrc != calculatedCrc;
    }

    // apufunktio sekvenssinumerolle
    private static int getSeq(byte[] packet) {
        return packet[0] & 0xFF;
    }

    // apufunktio tekstille
    private static String getText(byte[] packet, int length) {
        return new String(packet, 1, length - 2, StandardCharsets.UTF_8);
    }
}