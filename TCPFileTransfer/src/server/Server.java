package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.System.out;

public class Server extends Thread{
    private ServerSocket serverSocket;
    ExecutorService connectionsPool;
    public Server(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        connectionsPool = Executors.newCachedThreadPool();
out.println("listening on port " + port);

    }

    @Override
    public void run() {
        try {
            while (true){
                Socket clientSocket = null;

                clientSocket = serverSocket.accept();

                connectionsPool.execute(new Client(clientSocket));
            }
        } catch (IOException e) {}

        connectionsPool.shutdown();
    }

    public void terminate(){
        try {
            serverSocket.close();
        } catch (IOException e) {
        }
    }

}
