import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * selective repeat protokolla UDP:n päälle
 *
 * Ero stop-and-waitiin eli GBNLayeriin
 * - lähettäjä voi lähettää useita paketteja ennen ACKeja
 * - vastaanottaja voi hyväksyä myös väärässä järjestyksessä tulevia paketteja,
 *   jos ne ovat vastaanottoikkunan sisällä
 * - vastaanottaja bufferoi paketit ja toimittaa ne sovellukselle järjestyksessä
 * - ACKit ovat pakettikohtaisia
 */
public class SRLayer {

    private final DatagramSocket socket;

    private static final int TIMEOUT = 1000;
    private static final int BUFFER_SIZE = 256;
    private static final int WINDOW_SIZE = 4; // ikkunan koko > max 4 pakettia ennen kuin odotetan ack

    // lähettäjön 
    private int sendBase = 0;
    private int nextSeqNum = 0;

    // tallennetaan lähetetyt paketit uudelleenlähetystä varten
    private final Map<Integer, byte[]> sentPackets = new HashMap<>();

    // merkitään mitkä paketit on ackattu
    private final Set<Integer> ackedPackets = new HashSet<>();

    // vastanottajan > ikkunan alku
    private int rcvBase = 0;

    // bufferi väärässä järjestyksessä tulleille mutta hyväksytyille paketeille
    private final Map<Integer, String> receiveBuffer = new HashMap<>();

    // merkitään mitkä paketit on jo vastaanotettu
    private final Set<Integer> receivedPackets = new HashSet<>();

    public SRLayer(DatagramSocket socket) {
        this.socket = socket;
    }

//lähettäjän funktio!
    public void sendMessages(String[] messages, InetAddress address, int port) throws IOException {
        socket.setSoTimeout(TIMEOUT); 

        // mikä indeksi seuraavaksi lähetettävälle viestille ->
        int messageIndex = 0;

        // jatketaan kunnes kaikki viestit on lähetetty ja ackattu
        while (sendBase < messages.length) {

            // Lähetetään uusia paketteja niin kauan kuin lähetysikkunassa on tilaa
            while (nextSeqNum < sendBase + WINDOW_SIZE && messageIndex < messages.length) {
                byte[] packetBytes = makePacket(nextSeqNum, messages[messageIndex]);

                DatagramPacket packet = new DatagramPacket(
                        packetBytes,
                        packetBytes.length,
                        address,
                        port
                );

                socket.send(packet);
                sentPackets.put(nextSeqNum, packetBytes); // tallennetaan lähetetty paketti uudelleenlähetystä varten

                System.out.println("Sent data seq=" + nextSeqNum + ": " + messages[messageIndex]);

                nextSeqNum++; // kasvatetaan sekvenssinumeroa ja lähetettyjen viestien indeksiä
                messageIndex++;
            }

            // Ikkuna täynnä tai kaikki viestit lähetetty > odotetaan ACK
            try {
                byte[] buffer = new byte[BUFFER_SIZE];
                DatagramPacket ackPacket = new DatagramPacket(buffer, buffer.length);

                // Odotetaan ACK
                socket.receive(ackPacket);

                int length = ackPacket.getLength();

                // tarkistetaan onko ACK paketti tyhjä tai onko tapahtunut bittivirhe
                if (isCorrupted(buffer, length)) {
                    System.out.println("ACK corrupted, ignoring");
                    continue;
                }

                String text = getText(buffer, length);

                // lähettäjä odottaa ACK-paketteja muita ei hyväksitä
                if (!text.equals("ACK")) {
                    System.out.println("Non-ACK packet received, ignoring");
                    continue;
                }

                int ackSeq = getSeq(buffer);

                // Selective Repeatissä ACK koskee yksittäistä pakettia > ei kumulatiivinen (vrt GBN)
                if (ackSeq >= sendBase && ackSeq < sendBase + WINDOW_SIZE) {
                    System.out.println("Received ACK for seq " + ackSeq);

                    ackedPackets.add(ackSeq);

                    // Jos ACKattiin lähetysikkunan alku, siiretään ikkunaa eteenpäin
                    while (ackedPackets.contains(sendBase)) {
                        ackedPackets.remove(sendBase);
                        sentPackets.remove(sendBase);
                        sendBase++;

                        System.out.println("Sender window moved > sendBase=" + sendBase);
                    }
                } else {
                    System.out.println("ACK seq " + ackSeq + " outside sender window, ignoring");
                }

            } catch (SocketTimeoutException e) {
                /*
                 * simppleli Selective Repeat -timeout:
                 * lähetetään uudelleen vain ne nykyisen ikkunan paketit,
                 * joita ei ole ACKattu.
                 * Täydellisessä SR:ssä jokaisella paketilla olisi oma timer?
                 */
                System.out.println("Timeout, resending unACKed packets in sender window");

                for (int seq = sendBase; seq < nextSeqNum; seq++) {
                    if (!ackedPackets.contains(seq)) {
                        byte[] packetBytes = sentPackets.get(seq);

                        if (packetBytes != null) {
                            DatagramPacket packet = new DatagramPacket(
                                    packetBytes,
                                    packetBytes.length,
                                    address,
                                    port
                            );

                            socket.send(packet);
                            System.out.println("Resent data seq=" + seq);
                        }
                    }
                }
            }
        }

        socket.setSoTimeout(0);
        System.out.println("All messages sent with Selective Repeat");
    }

    /**
     *vastaanottajan funktio!
     * SR voi vastaanottaa paketteja väärässä järjestyksessä
     * Ne bufferöidään, mutta sovellukselle viedäään vain oikeassa järjestyksessä olevat paketit
     */
    public String receive() throws IOException {
        while (true) {
            byte[] rec = new byte[BUFFER_SIZE];
            DatagramPacket paketti = new DatagramPacket(rec, rec.length);

            socket.receive(paketti);

            int length = paketti.getLength();

            // tarkistetaan onko paketti tyhjä tai onko tapahtunut bittivirhe
            if (isCorrupted(rec, length)) {
                // jos on ignorataan. huom ei lähetetä ACKkia, koska lähettäjä odottaa vain ACKeja onnistuneista paketeista
                System.out.println("Corrupted packet, ignoring");
                continue;
            }

            String text = getText(rec, length);

            // vastaanottaja odottaa DATA-paketteja > jos ei, ignorataan
            if (text.equals("ACK")) {
                continue;
            }

            int seq = getSeq(rec);

    // vastaanotettu paketti on ikkunan sisällä > hyväksytään
            if (seq >= rcvBase && seq < rcvBase + WINDOW_SIZE) {
                System.out.println("Received packet seq=" + seq + " inside receiver window");

                // kokainen oikein vastaanotettu paketti ACKataan erikseen
                sendAck(seq, paketti.getAddress(), paketti.getPort());

                // jos pakettia ei ole vastaanotettu aiemmin, bufferöidään se eli lisätään vastaanotettujen joukkoon ja tallennetaan bufferiin
                if (!receivedPackets.contains(seq)) {
                    receiveBuffer.put(seq, text);
                    receivedPackets.add(seq);
                    System.out.println("Buffered packet seq=" + seq + ": " + text);
                }

                /*
                 * Jos rcvBase on nyt saatavilla, voidaan viedä data sovellukselle
                 * ensimmäinen järjestyksessä oleva paketti on aina rcvbase, koska muuten ikkunaa ei olisi siirretty eteenpäin
                 */
                if (receivedPackets.contains(rcvBase)) {
                    String deliverText = receiveBuffer.remove(rcvBase);
                    receivedPackets.remove(rcvBase);

                    System.out.println("Delivering packet seq=" + rcvBase + " to application");

                    rcvBase++;

                    return deliverText;
                }

 // jo vastaanotettu paketti ikkunan sisällä > ACKataan uudestaan mutta ei bufferöidä uudestaan eikä toimiteta sovellukselle
            } else if (seq >= rcvBase - WINDOW_SIZE && seq < rcvBase) {
                System.out.println("Received old packet seq=" + seq + ", sending ACK again");
                sendAck(seq, paketti.getAddress(), paketti.getPort());

                // paketti on vastaanottoikkunan ulkopuolella > ignorataan kokonaan, ei ACKkia eikä bufferointia
            } else {
                System.out.println("Packet seq=" + seq + " outside receiver window, ignoring");
            }
        }
    }

    // metodi ACKin lähettämiseen
    private void sendAck(int seq, InetAddress address, int port) throws IOException {
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

        byte crc = CRC8.calculate(packet, packet.length - 1);
        packet[packet.length - 1] = crc;

        return packet;
    }

// apufunktio paketin tarkistamiseen > onko tyhjä tai onko tapahtunut bittivirhe
    public static boolean isCorrupted(byte[] packet, int length) {
        if (length < 2) {
            return true;
        }

        byte receivedCrc = packet[length - 1];
        byte calculatedCrc = CRC8.calculate(packet, length - 1);

        return receivedCrc != calculatedCrc;
    }
// apufunktio tekstille
    private static String getText(byte[] packet, int length) {
        return new String(packet, 1, length - 2, StandardCharsets.UTF_8);
    }
// apufunktio sekvenssinumerolle
    private static int getSeq(byte[] packet) {
        return packet[0] & 0xFF;
    }
}