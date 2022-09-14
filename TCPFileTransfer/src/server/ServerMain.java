package server;

import java.io.IOException;
import java.util.Scanner;

public class ServerMain {
    public static void main(String[] args) {
        int port = args.length < 1 ? 54333 : Integer.parseInt(args[0]);

        try {
            Server server = new Server(port);
            server.start();

            Scanner in = new Scanner(System.in);
            while(true){
                String cmd = in.nextLine();
                String[] parsedCmd = cmd.split(" ");
                if (parsedCmd[0].equals("exit")){
                    server.terminate();
                    break;
                }
            }
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
