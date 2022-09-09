import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.TimerTask;

public class MulticastSender extends TimerTask {
    private final DatagramSocket socket;
    private final InetAddress group;
    private byte[] buffer;

    public MulticastSender(DatagramSocket socket, InetAddress group){
        this.socket = socket;
        this.group = group;
        this.buffer = "hello".getBytes();
    }


    public void run() {
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, 54445);
        try {
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
