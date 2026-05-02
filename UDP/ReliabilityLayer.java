import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * toteutetaan luokka reliabilitylayer
 * käyteään ack ja nack paketteja varmistamaan datan luotettava siirto
 * käytetään datagrampacket luokan metodeja
 */
public class ReliabilityLayer {
    private final DatagramSocket socket;


    public ReliabilityLayer(DatagramSocket socket) {
        this.socket = socket;
    }
// luodaan funktio paketin lähettämiseen 
    public void send(String message, InetAddress address, int port) throws IOException {
        byte[] packetBytes = makePacket((byte) 0, message); // luodaan paketti sekvenssinumerolla 0, tekstillä ja crcllä

        while (true) {
            DatagramPacket packet = new DatagramPacket(
                    packetBytes,
                    packetBytes.length,
                    address,
                    port
            );

            socket.send(packet);

            byte[] buffer = new byte[256];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length); // luodaan paketti ack/nack vastaanottoa varten

            socket.receive(response);

            int length = response.getLength();

            // tarkistetaan onko ack/nack paketti tyhjä tai onko tapahtunut bittivirhe
            if (isCorrupted(buffer, length)) {
                System.out.println("ACK/NACK corrupted, resending DATA");
                continue;
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
                System.out.println("CRC ERROR, sending NACK");

                byte[] nackBytes = makePacket((byte) 0, "NACK"); // luodaan nack paketti sekvenssinumerolla 0 ja teksitllä nack
                
                // luodaan nackille datagrampaketti joka lähtetetään takaisin lähettäjälle
                DatagramPacket nack = new DatagramPacket(
                        nackBytes, 
                        nackBytes.length,
                        paketti.getAddress(), // lähettäjän osoite ja portti
                        paketti.getPort()
                );

                socket.send(nack); // lähetetään paketti jossa teksti nak takasin lähettäjälle
                continue;
                // huom korruptoitunutta pakettia ei lähetetä sovelluselle
            }
        
            String text = getText(rec, length);

            // nack ack paketteja ei käsitellä vaan ne ohitetan
            if (text.equals("ACK") || text.equals("NACK")) {
                continue;
            }
            System.out.println("crc ok, sending ack");;
            byte[] ackBytes = makePacket((byte)0, "ACK"); 

                // luodaan ack paketti
                DatagramPacket ack = new DatagramPacket(
                        ackBytes,
                        ackBytes.length,
                        paketti.getAddress(),
                        paketti.getPort()
                );
                
                socket.send(ack);

                return text; // palautetaan vastaanotettu teksti sovellukselle!!!
            } 
        }

// tarkistetaaan onko paketti tyhjä tai onko tapahtunut bittivirhe
    public static boolean isCorrupted(byte[] packet, int length) {
        if (length < 1) {
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
        return (new String(packet, 0, length - 1, StandardCharsets.UTF_8));
    }

    // sekvenssinumerot alusta
    private static byte getSeq(byte[] packet) {
        return packet[0];
    }

    public String receiveOnlyAck() throws IOException {
        while (true) {
            byte[] rec = new byte[256];
            DatagramPacket response = new DatagramPacket(rec, rec.length);

            socket.receive(response);

            int length = response.getLength();

            if (isCorrupted(rec, length)) {
                System.out.println("crc error no ack sent");
                continue;
            }
            String text = getText(rec, length);

            if (text.equals("ACK") || text.equals("NACK")) {
                continue;
            }

            System.out.println("crc ok, sending ack");
            byte[] ackBytes = makePacket((byte)0, "ACK");
                DatagramPacket ack = new DatagramPacket(
                        ackBytes,
                        ackBytes.length,
                        response.getAddress(),
                        response.getPort()
                );
                
                socket.send(ack);
                return text;
        }
    }
}