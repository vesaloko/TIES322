import java.io. *;
import java.net.*;

/**
 * UDP virtual socketin testisovellus
 * lähetetty data näytetään konsolissa
 */
class TestApp {
    private static DatagramSocket soketti = null; // luodaan datagrammisoketti
    public static void main(String[] args) throws IOException {
       // soketti = new DatagramSocket(50267); // alustetaan soketti haluttuun porttiin
        soketti = new VirtualSocket(50267); // alustetaan virtuaalinen soketti
        boolean listening = true;
        while (listening) {
            try {
                byte[] rec = new byte[256];
                DatagramPacket paketti = new DatagramPacket(rec, rec.length);   // luodaan datagrammipaketti haluttuun porttiin
                soketti.receive(paketti); // soketti odottaa datagrammipakettia

                int length = paketti.getLength();
                if (length < 1) {
                        System.out.println("Tyhjä paketti");
                        continue;
                    }
                byte receivedCrc = rec[length - 1];
                byte calculatedCrc = CRC8.calculate(rec, length - 1);

                byte seq = rec[0]; // sekvenssinumero on ensimmäisessä tavussa


                if (receivedCrc == calculatedCrc) {
                    System.out.println("CRC OK");
                    System.out.println("Received: " + (new String(rec, 0, length - 1)));
                    System.out.println("seq"+ seq); // sekvenssinumero on ensimmäisessä tavussa
                } else {
                    System.out.println("CRC ERROR");
                    System.out.println("Received CRC: " + (receivedCrc & 0xFF));
                    System.out.println("Calculated CRC: " + (calculatedCrc & 0xFF));
                }         


                // System.out.println("Received: " + (new String(rec, 0, paketti.getLength()-1))); // tulostetaan vastaanotettu data
                // System.out.println("vika tavu: " + rec[paketti.getLength()-1]); // crc tavu kokonaislukuna
            }
            catch (IOException e) {
                listening = false;
                System.out.println("catch");
                break;
            }
        }
    }
}
