package server;

import protocol.Request;
import protocol.Response;

import java.io.*;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.System.currentTimeMillis;
import static java.lang.System.out;

public class Client implements Runnable{

    private final Socket socket;
    private final SocketAddress address;

    private String fileName;
    private long fileSize;
    private AtomicLong bytesTemp;
    private AtomicLong bytesTotal;
    private long startTime;
    private long endTime;

    SimpleDateFormat formatTime = new SimpleDateFormat("yyyyMMdd_hhmmss_");

    public Client(Socket socket){
        this.socket = socket;
        address = socket.getRemoteSocketAddress();
        bytesTemp = new AtomicLong(0);
        bytesTotal = new AtomicLong(0);
    }

    private byte[] upload(FileOutputStream file, InputStream client) throws NoSuchAlgorithmException, IOException {
        byte[] bytes = new byte[4096];
        MessageDigest md = MessageDigest.getInstance("MD5");
        DigestInputStream checksumStream = new DigestInputStream(client, md);
        int readBytes = 0;
        startTime = System.currentTimeMillis();
        for (long i = 0; i < fileSize; i += readBytes) {
            readBytes = checksumStream.read(bytes);
            file.write(bytes, 0, readBytes);
            bytesTemp.addAndGet(readBytes);
            bytesTotal.addAndGet(readBytes);
        }
        endTime = System.currentTimeMillis();
        file.close();
        return md.digest();
    }

    public static String formatBytes(long size) {
        String hrSize = null;

        double b = size;
        double k = size/1024.0;
        double m = ((size/1024.0)/1024.0);
        double g = (((size/1024.0)/1024.0)/1024.0);
        double t = ((((size/1024.0)/1024.0)/1024.0)/1024.0);

        DecimalFormat dec = new DecimalFormat("0.00");

        if ( t>1 ) {
            hrSize = dec.format(t).concat(" TB/s");
        } else if ( g>1 ) {
            hrSize = dec.format(g).concat(" GB/s");
        } else if ( m>1 ) {
            hrSize = dec.format(m).concat(" MB/s");
        } else if ( k>1 ) {
            hrSize = dec.format(k).concat(" KB/s");
        } else {
            hrSize = dec.format(b).concat(" Bytes/s");
        }

        return hrSize;
    }

    private void speedo(){
        long bytes = bytesTemp.getAndSet(0);
        out.println("[" + address.toString() + "] uploading speed:");
        out.println("current: " + formatBytes(bytes) + "  |  average: " + formatBytes(Math.round((double)bytesTotal.get()/((double)(currentTimeMillis()-startTime)/1000.))));
    }

    @Override
    public void run() {

out.println(address + " connected");

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        try {
            Request request = Request.readFrom(socket.getInputStream());
            if (request instanceof Request.Upload) {

out.println(address + " requested uploading");

                OutputStream outputStream = socket.getOutputStream();

                if (((Request.Upload) request).getCompression() != 0){
out.println(address + " rejected");
                    new Response.Reject("No compression supported yet").writeTo(outputStream);
                    socket.close();
                    return;
                }
                fileName = ((Request.Upload) request).getFileName();
                fileSize = ((Request.Upload) request).getFileSize();
                fileName = Paths.get(fileName).getFileName().toString();
                new File("uploads").mkdirs();
                File file = new File("uploads/" + fileName.trim());
                while (file.exists()) {
                    Date timeNow = new Date();
                    file = new File("uploads/" + formatTime.format(timeNow) + fileName.trim());
                }
                if (!file.getAbsolutePath().equals(file.getCanonicalPath()) || file.isAbsolute())
                {
out.println(address + " rejected");
                    new Response.Reject("Path traversal attempt").writeTo(outputStream);
                    socket.close();
                    return;
                }
                file.createNewFile();

                new Response.Accept().writeTo(outputStream);
out.println(address + " accepted to upload");

                executor.scheduleAtFixedRate(this::speedo, 1, 1, TimeUnit.SECONDS);
                byte[] checksum = new byte[0];
                try {
                    checksum = upload(new FileOutputStream(file), socket.getInputStream());
                }catch (IOException e){
                    file.delete();
                }
                Response response;
                if (Arrays.equals(checksum, ((Request.Upload) request).getHash())) {
                    response = new Response.Success(file.getPath());
out.println(address + " succeeded to upload");
                } else {
                    response = new Response.Failure();
out.println(address + " failed to upload");
                }

                out.println(address.toString() + " average uploading speed: " + formatBytes(Math.round((double)bytesTotal.get()/((double)(endTime-startTime)/1000.))));

                response.writeTo(outputStream);
                socket.close();
                executor.shutdown();
                return;
            }

        } catch (FileNotFoundException ex) {
        } catch (IOException ex) {
        } catch (NoSuchAlgorithmException ex) {
        }
out.println(address + " caused exception, terminating");
        executor.shutdown();
        try {
            socket.close();
        } catch (IOException e) {

        }
    }
}
