import java.io. *;
import java.net.*;
import java.util.Scanner;

/**
 * UDP virtual socketin testisovellus
 * lähetetty data näytetään konsolissa
 */
class TestApp {
    private static DatagramSocket soketti = null; // luodaan datagrammisoketti

    public static void main(String[] args) throws IOException {
        soketti = new VirtualSocket(50267); // alustetaan virtuaalinen soketti
        SRLayer srLayer = new SRLayer(soketti);
        boolean listening = true;

        while (listening) {
            try {
                String message = srLayer.receive();
                System.out.println("THE TEST APPLICATION RECEIVED: " + message);
            }
            catch (IOException e) {
                listening = false;
                System.out.println("catch");
                 break;
               }
        }
    }
}
