import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ProxyServer extends Thread{

    private static final int BUFFER_SIZE = 1 << 16;
    private int lastID = 0;
    private final Map<Integer, AbstractMap.SimpleEntry<SelectionKey,Long>> dnsQueue = new ConcurrentHashMap<>();

    private static Logger log = LogManager.getLogger(ProxyServer.class);
    private ServerSocketChannel serverSocket;
    private Selector selector;
    private DatagramChannel dnsChannel;
    private SelectionKey dnsKey;
    private InetSocketAddress dnsAddress;
    ScheduledExecutorService executor;

    public ProxyServer(int port) throws IOException {

        selector = Selector.open();

        serverSocket = ServerSocketChannel.open();
        serverSocket.configureBlocking(false);
        serverSocket.bind(new InetSocketAddress(port));
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);


        dnsChannel = DatagramChannel.open();
        dnsChannel.bind(new InetSocketAddress(0));
        dnsChannel.configureBlocking(false);
        dnsKey = dnsChannel.register(selector, SelectionKey.OP_READ);

        try{

            dnsAddress = new InetSocketAddress(ResolverConfig.getCurrentConfig().server(),53);
        }catch (IllegalArgumentException e){
            log.error("Cannot resolve DNS server");
            throw new RuntimeException("Cannot resolve DNS server");
        }

        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::checkDNSqueue, 60, 60, TimeUnit.SECONDS);

        System.out.println("DNS:" + dnsAddress);
        log.info("Server initialized");

    }

    private void checkDNSqueue(){
        long time = System.currentTimeMillis() - 60000;
        var c = dnsQueue.values();
        for ( var i : dnsQueue.values()){
            if (i.getValue()<time){
                if (c.remove(i)){
                    close(i.getKey());
                    log.info("DNS didn't respond");
                }
            }
        }
    }

    @Override
    public void run() {

        log.info("Starting");

        while (!isInterrupted()) {
            try {
                selector.select(this::performActionOnKey);
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }

        try {
            selector.close();
        } catch (IOException e) {

        }
        executor.shutdown();
        log.info("Stopping");

    }


    public Set<SelectionKey> getKeys(){
        return Collections.unmodifiableSet(selector.keys());
    }

    public int getKeysCount(){
        return selector.keys().size();
    }


    private void performActionOnKey(SelectionKey key) {
        try {
            if (!key.isValid()) {
                log.warn("SELECT ERROR: invalid key");
            } else if (key.isAcceptable()) {
                accept(key);
            } else if (key.isReadable()) {
                if (key.equals(dnsKey)) {
                    getPeerIpAddress(key);
                } else {
                    read(key);
                }
            } else if (key.isWritable()) {
                write(key);
            } else if (key.isConnectable()) {
                connect(key);
            }
        } catch (IOException e) {
            close(key);
        }
    }






    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        log.debug("Accepting: " + serverSocketChannel.getLocalAddress());
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        socketChannel.register(key.selector(), SelectionKey.OP_READ);

    }







    private void read(SelectionKey key){
        var connection = (Connection) key.attachment();
        if (connection == null) {
            connection = new Connection();
            connection.state = Connection.State.METHOD;
            connection.in = ByteBuffer.allocate(BUFFER_SIZE);
            key.attach(connection);
        }
        if (connection.inputShutdown){
            key.interestOpsAnd(~SelectionKey.OP_READ);
            return;
        }
        try {
            var channel = (SocketChannel) key.channel();

            int s = channel.read(connection.in);
            //try {log.info("read " + s + " " + ((SocketChannel) key.channel()).getRemoteAddress());} catch (IOException e){ log.info("Cannot log write"); }
            if (s < 0) {
                switch (connection.state){
                    case METHOD:
                    case CONNECTION:
                        close(key);
                        break;
                    case TRANSFER:
                        closeInput(key);
                        break;
                }
            } else if (connection.state == Connection.State.METHOD) {
                scanAndReplyAuthRequest(key);
            } else if (connection.state == Connection.State.CONNECTION) {
                scanAndReplyConnectionRequest(key);
            } else {
                if(s>0)connection.in.flip();
                connection.peerKey.interestOpsOr(SelectionKey.OP_WRITE);
                key.interestOpsAnd(~SelectionKey.OP_READ);

            }
        }
        catch (IOException e){
            log.info("IOException in read");
            close(key);
        }
    }






    private void scanAndReplyAuthRequest(SelectionKey key){
        Connection connection = (Connection) key.attachment();

        var len = connection.in.position();
        if (len < 2) {
            return;
        }

        var data = connection.in.array();


        boolean noAuthMethodFound = true;

        if (data[0] != 0x05) {
            log.info("Auth request has invalid version, only SOCKS5 is supported");
        }
        else {
            var methods_count = data[1];
            if (len - 2 < methods_count) {
                return;
            }

            for (int method_i = 0; method_i < methods_count; method_i++) {
                var method = data[method_i + 2];
                if (method == 0x00) {
                    noAuthMethodFound = false;
                    break;
                }
            }
        }
        byte[] answer = new byte[]{0x05,0x00};
        if (noAuthMethodFound){
            answer[1]=(byte)0xFF;
            connection.closed = true;
        }

        connection.out = ByteBuffer.allocate(BUFFER_SIZE);
        connection.out.put(answer).flip();
        key.interestOps(SelectionKey.OP_WRITE);
        connection.in.clear();
    }









    private void scanAndReplyConnectionRequest(SelectionKey key) {
        Connection connection = (Connection) key.attachment();
        var len = connection.in.position();
        if (len < 4) {
            return;
        }

        var data = connection.in.array();
        byte error = 0x00;
        if (data[0] != 0x05) {
            error = 0x01;
        }

        if (data[1] != 0x01) {
            error = 0x07;
        }

        if (error==0x00) {
            if (data[3] == 0x01) {
                var connectAddrBytes = new byte[]{data[4], data[5], data[6], data[7]};
                InetAddress connectAddr = null;
                try {
                    connectAddr = InetAddress.getByAddress(connectAddrBytes);
                } catch (UnknownHostException e) {
                    error = 0x04;
                    connection.closed = true;
                    connection.out.clear();
                    connection.out.put(new byte[]{0x05,error,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00}).flip();
                    key.interestOps(SelectionKey.OP_WRITE);

                    log.info("Connection error: " + (int)error);
                    connection.in.clear();
                    return;
                }
                var portPos = 8;
                connection.peerPort = ((data[portPos] & 0xFF) << 8) + (data[portPos + 1] & 0xFF);

                try {
                    registerPeer(connectAddr, key);
                } catch (IOException e) {
                    error = 0x04;
                }
            } else if (data[3] == 0x03) {
                var hostLen = data[4];
                var hostStart = 5;
                if (len < hostLen + hostStart + 2) {
                    return;
                }
                var host = new String(Arrays.copyOfRange(data, hostStart, hostStart + hostLen));
                var portPos = hostStart + hostLen;
                var connectPort = ((data[portPos] & 0xFF) << 8) + (data[portPos + 1] & 0xFF);


                try {
                    requestHostResolve(host, connectPort, key);
                } catch (IOException e) {
                    error = 0x04;
                }
            }
            else{
                error = 0x08;
            }
        }
        if (error != 0x00){
            connection.closed = true;
            connection.out.clear();
            connection.out.put(new byte[]{0x05,error,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00}).flip();
            key.interestOps(SelectionKey.OP_WRITE);

            log.info("Connection error: " + (int)error);
        }
        else {
            log.info("Connection approved");
            key.interestOps(0);
        }
        connection.in.clear();
    }






    private void write(SelectionKey key) {
        SocketChannel channel = ((SocketChannel) key.channel());
        Connection connection = ((Connection) key.attachment());

        int s = 0;
        try {
            s = channel.write(connection.out);
            connection.out.clear();
        } catch (IOException e) {
            connection.closed = true;
        }

        //try { log.info("write " + s + " " + ((SocketChannel) key.channel()).getRemoteAddress()); } catch (IOException e) { log.info("Cannot log write"); }
        try {
            if (connection.closed) {
                close(key);
                return;
            } else
            if (connection.outputShutdown){
                key.interestOpsAnd(~SelectionKey.OP_WRITE);
                try {
                    ((SocketChannel)key.channel()).shutdownOutput();
                } catch (IOException e) {
                }
                connection.out.clear();
                return;
            }
            if (s == -1) {
                switch (connection.state) {
                    case METHOD:
                    case CONNECTION:
                        close(key);
                        break;
                    case TRANSFER:
                        closeOutput(key);
                        break;
                }
            } else {
                switch (connection.state) {
                    case METHOD:
                        log.info("Write method response to " + channel.getRemoteAddress());

                        key.interestOps(SelectionKey.OP_READ);
                        connection.state = Connection.State.CONNECTION;
                        break;
                    case CONNECTION:
                        log.info("Write connection response to " + channel.getRemoteAddress());
                        key.interestOps(SelectionKey.OP_READ);
                        Connection peerConnection = (Connection) connection.peerKey.attachment();
                        connection.state = Connection.State.TRANSFER;
                        peerConnection.state = Connection.State.TRANSFER;
                        peerConnection.in = connection.out;
                        peerConnection.out = connection.in;
                        connection.peerKey.interestOps(SelectionKey.OP_READ);
                        break;
                    case TRANSFER:
                        key.interestOpsAnd(~SelectionKey.OP_WRITE);
                        connection.peerKey.interestOpsOr(SelectionKey.OP_READ);
                        connection.out.clear();
                        break;
                }
            }
        }catch (IOException e){
            log.warn("cannot get channel.getRemoteAddress()");
        }
    }





    private void closeOutput(SelectionKey key) {
        SocketChannel channel = ((SocketChannel) key.channel());
        Connection connection = ((Connection) key.attachment());

        if (connection.peerKey == null){
            log.info("No auth - client closed connection");
            close(key);
            return;
        }
        Connection peer = (Connection) connection.peerKey.attachment();
        try {
            log.info("Closing output with" + channel.getRemoteAddress());
            channel.shutdownOutput();
            ((SocketChannel)connection.peerKey.channel()).shutdownInput();

        } catch (IOException e) {
        }
        peer.inputShutdown = true;
        connection.outputShutdown = true;
        connection.peerKey.interestOpsAnd(~SelectionKey.OP_READ);
        key.interestOpsAnd(~SelectionKey.OP_WRITE);
        if (connection.inputShutdown) {
            close(key);
        }
    }


    private void closeInput(SelectionKey key) {
        SocketChannel channel = ((SocketChannel) key.channel());
        Connection connection = ((Connection) key.attachment());

        Connection peer = (Connection) connection.peerKey.attachment();
        try {
            log.info("Closing input with " + channel.getRemoteAddress());
            channel.shutdownInput();
            connection.in.flip();
            key.interestOpsAnd(~SelectionKey.OP_READ);
            connection.peerKey.interestOpsOr(SelectionKey.OP_WRITE);
            peer.outputShutdown = true;
        } catch (IOException e) {
        }

        if (connection.outputShutdown) {
            close(key);
        }
    }

    private void closeSocket(SelectionKey key){
        key.cancel();
        try {
            ((Connection)((Connection)key.attachment()).peerKey.attachment()).peerKey = null;
            log.info("Closing socket with " + ((SocketChannel)key.channel()).getRemoteAddress());
            key.channel().close();
        } catch (IOException e) {
        }
    }

    private void close(SelectionKey key){
        key.cancel();
        try {
            log.info("Closing connection with " + ((SocketChannel)key.channel()).getRemoteAddress());
            key.channel().close();
        } catch (IOException e) {
        }
        SelectionKey peerKey = ((Connection) key.attachment()).peerKey;
        if (peerKey != null && !peerKey.equals(key)) {
            ((Connection) peerKey.attachment()).peerKey = null;
            close(peerKey);
        }
    }







    private void connect(SelectionKey key) throws IOException {



        var channel = ((SocketChannel) key.channel());
        try{channel.finishConnect();}
        catch (IOException e){}

        var peerConnection = ((Connection) key.attachment());

        var clientConnection = (Connection)peerConnection.peerKey.attachment();

        if (clientConnection.outputShutdown || !peerConnection.peerKey.isValid() || !key.isValid()){
            close(key);
            log.info("Client aborted");
            return;
        }

        if(!channel.isConnected()){
            clientConnection.out.clear();
            clientConnection.out.put(new byte[]{0x05,0x01,0x00,0x01,0x00,0x00,0x00,0x00,0x00,0x00}).flip();
            log.info("Connection to remote host failed");
            key.interestOps(0);
            clientConnection.closed = true;
            peerConnection.peerKey.interestOps(SelectionKey.OP_WRITE);
            return;
        }
        var address = ((InetSocketAddress)((SocketChannel) key.channel()).getRemoteAddress());

        clientConnection.out.clear();
        clientConnection.out.put(new byte[]{0x05,0x00,0x00,0x01});
        clientConnection.out.put(address.getAddress().getAddress());
        clientConnection.out.put((byte) ((address.getPort()&0xFF00) >> 8));
        clientConnection.out.put((byte) (address.getPort()&0xFF)).flip();
        log.info("Connected to remote host " + address);
        key.interestOps(0);
        peerConnection.peerKey.interestOps(SelectionKey.OP_WRITE);
    }








    private void requestHostResolve(String host, int backPort, SelectionKey key) throws IOException {
        Connection connection = (Connection) key.attachment();
        connection.peerPort = backPort;
        log.info("Resolving host: " + host + ":"+ backPort + " for " + ((SocketChannel) key.channel()).getRemoteAddress());

        InetAddress a;

        if ((a = InetAddress.getByName(host)).toString().startsWith("/")){
            registerPeer(a, key);
            return;
        }

        Message message = new Message();
        message.addRecord(Record.newRecord(new Name((new String(host)) + "."), Type.A, DClass.IN), Section.QUESTION);

        Header header = message.getHeader();
        header.setOpcode(Opcode.QUERY);
        header.setID(lastID);
        header.setFlag(Flags.RD);

        byte[] wire = message.toWire();

        dnsQueue.put(lastID++, new AbstractMap.SimpleEntry<>(key, System.currentTimeMillis()));
        dnsChannel.send(ByteBuffer.wrap(wire, 0, wire.length), dnsAddress);
    }







    private void registerPeer(InetAddress connectAddress, SelectionKey backKey) throws IOException {
        int connectPort = ((Connection) backKey.attachment()).peerPort;
        log.info(String.format("Connecting to host %s:%d for %s", connectAddress, connectPort,((SocketChannel)backKey.channel()).getRemoteAddress()));

        var peer = SocketChannel.open();
        peer.configureBlocking(false);
        peer.connect(new InetSocketAddress(connectAddress, connectPort));
        var peerKey = peer.register(backKey.selector(), SelectionKey.OP_CONNECT);

        ((Connection) backKey.attachment()).peerKey = peerKey;
        Connection peerAttachment = new Connection();
        peerAttachment.peerKey = backKey;
        peerKey.attach(peerAttachment);

    }






    private void getPeerIpAddress(SelectionKey key) throws IOException{

        DatagramChannel channel = ((DatagramChannel) key.channel());
        ByteBuffer byteBuffer = (ByteBuffer.allocate(BUFFER_SIZE));
        channel.receive(byteBuffer);
        byteBuffer.flip();

        Message response = new Message(byteBuffer.array());

        if (dnsQueue.containsKey(response.getHeader().getID())) {
            Record[] records = response.getSectionArray(Section.ANSWER);
            SelectionKey regKey = dnsQueue.get(response.getHeader().getID()).getKey();

            if (records.length >= 1) {

                for (Record record : records) {
                    if (record.getType() == 1) {
                        registerPeer(InetAddress.getByAddress(record.rdataToWireCanonical()), regKey);
                        dnsQueue.remove(regKey);
                        log.info("Got peer ip for " + ((SocketChannel) regKey.channel()).getRemoteAddress());
                        return;
                    }
                }

            }

            Connection connection = (Connection)regKey.attachment();
            connection.closed = true;
            connection.out.clear();
            connection.out.put(new byte[]{0x05,0x04,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00}).flip();
            regKey.interestOps(SelectionKey.OP_WRITE);
            dnsQueue.remove(regKey);
            log.info("Did not get peer ip for " + ((SocketChannel) regKey.channel()).getRemoteAddress());
        }
    }









}
