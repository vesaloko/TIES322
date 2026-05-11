// testiapplikaatio ei vastannut enää GBN layerin tarpeisiin, kun sekvenssinumerot vaihtui pois 0/1 
import java.io.IOException;
import java.net.*;

public class SenderApp {

    public static void main(String[] args) throws IOException {
        DatagramSocket soketti = new VirtualSocket();
        SRLayer srLayer = new SRLayer(soketti);
        InetAddress receiverAddress = InetAddress.getLocalHost();
        int receiverPort = 50267;
        String[] messages = {

                "msg0",
                "msg1",
                "msg2",
                "msg3",
                "msg4",
                "msg5",
                "msg6",
                "msg7"
        };

        srLayer.sendMessages(messages, receiverAddress, receiverPort);
        soketti.close();
    }

}