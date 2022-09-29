import org.apache.logging.log4j.core.jmx.Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Main {


    public static void main(String[] args) {
        int port = (args.length < 1) ? 1080 : Integer.parseInt(args[0].trim());
        ProxyServer proxy;
        try {
            proxy = new ProxyServer(port);
        } catch (IOException e) {
            System.out.println("Cannot start server");
            return;
        }


        proxy.start();
/*
try {
    Socket socket = new Socket();
    socket.connect(new InetSocketAddress("127.0.0.1", port));
    var os = socket.getOutputStream();
    var is = socket.getInputStream();
    os.write(new byte[]{0x05,0x01,0x00});
    byte[] buffer = new byte[1024];
    is.read(buffer);
    System.out.println((int)buffer[0]+" "+ (int)buffer[1] + " " + (int)buffer[2]);
    os.write(new byte[]{0x05,0x01,0x00,0x01,(byte)87,(byte)250,(byte)250,(byte)242,0x00,(byte)80});
    is.read(buffer);
    for (int i =0;i<10;++i) {
        System.out.print((int) buffer[i] + " ");
    }
    System.out.println("");
    String request = new String("GET / HTTP/1.1\n" +
            "Host: ya.ru\n\n");
    os.write(request.getBytes(StandardCharsets.US_ASCII));
    socket.getOutputStream().close();
    int s = 0;
    if((s=is.read(buffer)) > 0){
        System.out.println("S:"+s);
    for (int i =0;i<s;++i) {
        System.out.print((char)buffer[i]);
    }
    }
    //socket.getInputStream().close();
    socket.close();
    System.out.println("Closed client");
}
catch (IOException e){

}*/
        Scanner in = new Scanner(System.in);
        while(true){
            String cmd = in.nextLine();
            String[] parsedCmd = cmd.split(" ");
            if (parsedCmd[0].equals("exit") || parsedCmd[0].equals("stop")){
                proxy.interrupt();
                break;
            }
            if(parsedCmd[0].equals("info") || parsedCmd[0].equals("stat")){
                System.out.println("Currently active keys:" + (proxy.getKeysCount()-2));
                for (var i:proxy.getKeys()) {
                    try {
                        if (i.channel() instanceof SocketChannel)
                            System.out.println(((SocketChannel)i.channel()).getRemoteAddress());
                    } catch (IOException e) {
                        System.out.println("Unknown connection");
                    }
                }
            }
        }
        in.close();
    }
}
