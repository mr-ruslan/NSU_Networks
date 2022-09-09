import java.io.IOException;
import java.net.*;
import java.util.Scanner;
import java.util.Timer;

public class MulticastClient {

    private MulticastSocket socket;
    private final DatagramSocket sendSocket;
    private final MulticastReceiver receiver;
    private final MulticastSender sender;
    private final Timer timer;
    private final InetAddress group;
    private final int port;
    private final Viewer viewer;

    public MulticastClient(String address, String port) throws IOException{
        this.port = Integer.parseInt(port);
        socket = new MulticastSocket(54445);
        sendSocket = new DatagramSocket(this.port);
        socket.setBroadcast(true);
        group = InetAddress.getByName(address);
        socket.joinGroup(new InetSocketAddress(group, 54445), NetworkInterface.getByInetAddress(group));

        viewer = new Viewer();
        receiver = new MulticastReceiver(socket, viewer);
        sender = new MulticastSender(sendSocket, group);
        timer = new Timer();
        timer.scheduleAtFixedRate(sender, 0, 1000);
        receiver.start();
        timer.scheduleAtFixedRate(viewer, 1000, 1000);
    }

    public void stop() throws IOException {
        timer.cancel();
        socket.leaveGroup(group);
        sendSocket.close();
        socket.close();
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Multicast group IP should be specified. Also port may be specified(default is 54444)");
            return;
        }

        try {
            MulticastClient client = new MulticastClient(args[0], (args.length > 1) ? args[1] : "54444");

            Scanner in = new Scanner(System.in);
            while(true){
                String cmd = in.nextLine();
                String[] parsedCmd = cmd.split(" ");
                if (parsedCmd[0].equals("exit")){
                    client.stop();
                    break;
                }

            }
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("try another broadcast port / the listening port (54445) is already in use");
        }
    }

}
