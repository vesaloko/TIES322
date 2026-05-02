import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * toteutetaan luokka reliabilitylayer mahdollistamaan luotettava datan siirto UDPn päällä stop-and-wait periaatteella
 * kurose & ross: computer networking: a top-down approach -kirjan mukaisesti täyttäen versio 3.0 vaatimukset
 * jokainen datapaketti on rakenteeltaan |seq | data | crc|
 * tarkistetaan vastaanotetut paketit ennen niiden lähettämistä sovellukselle bittivirheiden sekä packet lossin (tai viiveen) varalta 
 * bittivirheiden tunnistamiseksi käytettään crc8 tarkistussummaa. vastaanotetusta paketista lasketaan crc8 ja verrataan sitä paketin viimeiseen tavuun
 * myös sekvenssinumero tarkistetaan vertaamalla sitä odotettuun sekvenssinumeroon, jotta voidaan tunnistaa duplikaatti paketit ja varmistaa oikea järjestys
 * kuitataan vastaanotetut paketit lähettämällä ack lähettäjälle vastaavalla sekvenssinumerolla
 * korruptoituneelle tai duplikaattipaketille lähetetään ack edellisen hyväksytyn paketin sekvenssinumerolla, jotta lähettäjä tietää lähettää uudestaan
 * ehjät ja odotetut paketit kuitataan ackillä, ja siirretään testisovellukselle, jotta lähettäjä voi siirtyä seuraavaan pakettiin
 * muuten jäädään odottamaan uudestaan saapuvaa pakettia samalla odotetulla sekvenssinumerolla
 */


public class ReliabilityLayer {
    private final DatagramSocket socket;
    private byte seq = 0; // sekvenssinumero 0 tai 1
    private byte expectedSeq = 0; // odotettu sekvenssinumero vastaanotettaville paketeille

    private static final int TIMEOUT = 1000; 

    public ReliabilityLayer(DatagramSocket socket) {
        this.socket = socket;
    }

// luodaan funktio lähettämiseen 
    public void send(String message, InetAddress address, int port) throws IOException {
        byte[] packetBytes = makePacket(seq, message); // luodaan paketti sekvenssinumerolla, tekstillä ja crcllä

        socket.setSoTimeout(TIMEOUT); // asetetaan timeout

        while (true) {
            DatagramPacket packet = new DatagramPacket(
                    packetBytes,
                    packetBytes.length,
                    address,
                    port
            );

            socket.send(packet);
            System.out.println("Sent data: " + message + " with seq: " + seq);

            try {
                byte[] buffer = new byte[256];
                DatagramPacket ackPacket = new DatagramPacket(buffer, buffer.length); // luodaan datagrammipaketti ackille

                socket.receive(ackPacket);

                int length = ackPacket.getLength();

                // tarkistetaan onko ack paketti tyhjä tai onko tapahtunut bittivirhe
                if (isCorrupted(buffer, length)) {
                    System.out.println("ACK corrupted");
                    continue;
                }

                String text = getText(buffer, length);
                byte ackSeq = getSeq(buffer);

                if (text.equals("ACK") && ackSeq == seq)
                {
                    System.out.println("Received ACK for seq " + seq);
                    seq = (byte) (1 - seq); // päivitetään sekvenssinumero seuraavaksi
                    socket.setSoTimeout(0); // poistetaan timeout
                    return; // data on onnistuneesti vastaanotettu, palataan
                } else {
                    System.out.println("Received NACK or wrong ACK seq");
                }
            } catch (SocketTimeoutException e) {
                System.out.println("ACK timeout, resending packet with seq " + seq);
            }
        }
    }

    public String receive() throws IOException {
        while (true) {
            byte[] rec = new byte[256];
            DatagramPacket paketti = new DatagramPacket(rec, rec.length);
            socket.receive(paketti); // vastaanotetaatn paketti normaalisti

            int length = paketti.getLength();

            // tarkistetaan onko paketti tyhjä tai onko tapahtunut bittivirhe
            if (isCorrupted(rec, length)) {
                // on korruptoitunut, lähetettään ack edelliselle paketille ja jäädään odottamaan uuta pakettia samalla odotetulla sekvenssinumerolla. dataa ei lähetetä sovellukselle
                System.out.println("corrupted data, sending ack for previous packet" + " with seq " + (1 - expectedSeq));
                byte previousSeq = (byte) (1 - expectedSeq); // edellisen paketin sekvenssinumero

                byte[] ackBytes = makePacket(previousSeq, "ACK"); // luodaan ack paketti sekvenssinumerolla 0 ja teksitllä ack
                
                // luodaan nackille datagrampaketti joka lähtetetään takaisin lähettäjälle
                DatagramPacket ack = new DatagramPacket(
                        ackBytes, 
                        ackBytes.length,
                        paketti.getAddress(), // lähettäjän osoite ja portti
                        paketti.getPort()
                );

                socket.send(ack); // lähetetään ack 
                continue;
                // huom korruptoitunutta pakettia ei lähetetä sovelluselle
            }
        
            String text = getText(rec, length);

            // nack ack paketteja ei käsitellä vaan ne ohitetan
            if (text.equals("ACK") || text.equals("NACK")) {
                continue;
            }

            // tarkistetaan onko saatu paketti odotettu sekvenssinumero
            if (getSeq(rec) == expectedSeq) {
                // odotettu = saatu, lähetetään ack, päivitetään sekvenssinumero ja palautettaan data
                System.out.println("Received expected packet with seq " + expectedSeq + "as expected!");
                byte[] ackBytes = makePacket(expectedSeq, "ACK"); // luodaan ack paketti sekvenssinumerolla 0 ja teksitllä ack
                DatagramPacket ack = new DatagramPacket(
                        ackBytes,
                        ackBytes.length,
                        paketti.getAddress(),
                        paketti.getPort()
                );
                socket.send(ack); // lähetetään ack
                expectedSeq = (byte) (1 - expectedSeq); // päivitetään odotettu sekvenssinumero seuraavaksi
                System.out.println("Sent ack with seq " + getSeq(rec) + "now expecting packet with seq " + expectedSeq);
                return text; // palautetaan vastaanotettu teksti sovellukselle
                
            } else { // saatu != odotettu, duplikaatti. lähetetään ack edelliselle paketille ja jäädäään odottamaan utta pakettia samalla odotetulla sekvenssinumerolla. dataa ei lähetetä sovellukselle
               System.out.println("Received duplicate packet with seq " +  getSeq(rec)+ ", expected " + expectedSeq);
                    byte previousSeq = (byte) (1 - expectedSeq); // edellisen paketin sekvenssinumero
    
                    byte[] ackBytes = makePacket(previousSeq, "ACK"); 
                    
                    // luodaan ack datagrampaketti joka lähtetetään takaisin lähettäjälle
                    DatagramPacket ack = new DatagramPacket(
                            ackBytes, 
                            ackBytes.length,
                            paketti.getAddress(), // lähettäjän osoite ja portti
                            paketti.getPort()
                    );
                System.out.println("Sent ack for previous packet with seq " + previousSeq);
                    socket.send(ack); // lähetetään ack 
            } 
        }
    }

// tarkistetaaan onko paketti tyhjä tai onko tapahtunut bittivirhe
    public static boolean isCorrupted(byte[] packet, int length) {
        if (length < 2) { // kasvatetaan myös vaatimus paketin minimipituudelle > pitää olla ainakin crc ja sekvenssinumero
            System.out.println("Tyhjä paketti");
             return true;
        }
        byte receivedCrc = packet[length - 1];
        byte calculatedCrc = CRC8.calculate(packet, length - 1);

        return receivedCrc != calculatedCrc;
    }

    // luodaan funktio paketin muodostamiseen
    // koostuu sekvenssinumerosta tekstistä (NAK/ACK) ja crc
        private static byte[] makePacket(byte seq, String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8); 
        byte[] packet = new byte[1 + data.length + 1];

        packet[0] = seq;
        System.arraycopy(data, 0, packet, 1, data.length);

        byte crc = CRC8.calculate(packet, packet.length - 1);
        packet[packet.length - 1] = crc; //crc tavu paketin viimeiseksi

        return packet;
    }

    // funktio paketin tekstille
    private static String getText(byte[] packet, int length) {
        return (new String(packet, 1, length - 2, StandardCharsets.UTF_8));
    }

    // sekvenssinumerot alusta
    private static byte getSeq(byte[] packet) {
        return packet[0];
    }

}