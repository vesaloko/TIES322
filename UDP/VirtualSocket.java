import java.io.*;
import java.net.*;
import java.util.Random;

/**
 * virtuaalinen udp soketti
 * Reliability on top of UDP: Virtual Socket 2
 */

public class VirtualSocket extends DatagramSocket 
{
    private static double p_drop = 0.5; // pudotetun paketin todennäiköisyys

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
            Random rand = new Random();
            super.receive(paketti); // paketti vastaanotetaan normaalisti
            if (rand.nextDouble() <= p_drop) { // paketti pudotetaan 50% todennäköisyydellä
                System.out.println("Packet dropped");
            }
            else {
                System.out.println("Packet received");
                return;
            }
        }
    }
}