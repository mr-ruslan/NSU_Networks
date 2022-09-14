package server;

import protocol.Request;

public class Uploader extends Thread{
    Request.Upload request;

    public Uploader(Request.Upload request){
        this.request = request;
    }

    @Override
    public void run() {

    }
}
