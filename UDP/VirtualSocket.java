import java.io.*;
import java.net.*;
import java.util.Random;
import java.lang.Thread;

/**
 * virtuaalinen udp soketti
 * Reliability on top of UDP: Virtual Socket 2
 */

public class VirtualSocket extends DatagramSocket {
    private static final Random rand = new Random();
    private static double p_drop = 0; // pudotetun paketin todennäköisyys -> crc8 varten nolla
    private static double p_delay = 0; // viivästetyn paketin todennäköisyys  -> crc8 varten nolla
    private static double p_error = 0; // virheellisen paketin todennäiköisyys

    // constructor ilman porttinumeroa
    public VirtualSocket() throws SocketException {
        super();
    }
    // constructor porttinumerolla
    public VirtualSocket(int port) throws SocketException {
        super(port);    
    }

    // paketin vastaanotto jossa on mahdollisuus pudotta paketti
    public void receive(DatagramPacket paketti) throws IOException {
        while (true) {  
            super.receive(paketti); // paketti vastaanotetaan normaalisti
               if (rand.nextDouble() <= p_drop) { // paketti pudotetaan 50% todennäköisyydellä
                System.out.println("Packet dropped"); // does not pass the packet to the application
            }
            // jos pakettia ei pudoteta, se voidaan viivästyttää
            else {
                System.out.println("Packet received");
                if (rand.nextDouble() <= p_delay) { // paketti viivästetään 50% todennäköisyydellä
                     int delay = rand.nextInt(1000); // viivästys 0-1000 ms
                            try {
                                System.out.println("Packet delayed by " + delay + " ms");
                                Thread.sleep(delay);
                        }   catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                        }
                    }
                    if (paketti.getLength() > 0 && rand.nextDouble() <= p_error) { // generoidaan bittivirhe 50% todennäköisyydellä
                        byte[] data = paketti.getData();
                        int index = rand.nextInt(paketti.getLength() - 1); // satunnainen indeksi paketissa (ei viimeistä tavua = CRC)
                        int bit = rand.nextInt(8); // satunnainen bitti indeksin sisällä
                        data[index] ^= (1 << bit); // käännetään yksi bitti
                        System.out.println("Bit error generated at byte index " + index + ", bit " + bit);
                    } 
                return; // palataan käsittelemään loput paketit
            }
        }
    }
}
