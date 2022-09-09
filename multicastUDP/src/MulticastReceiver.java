import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;

public class MulticastReceiver extends Thread {
    private final MulticastSocket socket;
    protected byte[] buf = new byte[4];
    Viewer viewer;

    public MulticastReceiver(MulticastSocket socket, Viewer viewer) {
        this.socket = socket;
        this.viewer = viewer;
    }

    public void run() {
        while (true) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            try {
                socket.receive(packet);
                String message = new String(packet.getData());
                if (message.equals("hell")) {
                    viewer.alive(new InetSocketAddress(packet.getAddress(), packet.getPort()));
                }


            } catch (IOException e) {
                break;
            }
        }

    }

}
