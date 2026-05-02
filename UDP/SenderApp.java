import java.io.*;
import java.net.*;

/**
 * Simple UDP sender that sends packets with CRC
 */
class SenderApp {
    public static void main(String[] args) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName("localhost");

        // Test message
        String message = "Hello World!";
        byte[] data = message.getBytes();

        // Create packet with CRC
        byte[] packetWithCrc = new byte[data.length + 1];
        System.arraycopy(data, 0, packetWithCrc, 0, data.length);
        byte crc = CRC8.calculate(packetWithCrc, data.length);
        packetWithCrc[data.length] = crc;

        DatagramPacket packet = new DatagramPacket(packetWithCrc, packetWithCrc.length, address, 50267);
        socket.send(packet);

        System.out.println("Sent: " + message + " with CRC: " + crc);

        socket.close();
    }
}