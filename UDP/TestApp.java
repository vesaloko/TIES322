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
                System.out.println("Received: " + (new String(rec, 0, paketti.getLength()-1))); // tulostetaan vastaanotettu data

            }
            catch (IOException e) {
                listening = false;
                System.out.println("catch");
                break;
            }
        }
    }
}
