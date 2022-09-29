import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

public class Connection {

    public enum State{
        METHOD,
        CONNECTION,
        TRANSFER
    }

    public State state = State.METHOD;

    public ByteBuffer in;
    public ByteBuffer out;

    public SelectionKey peerKey;
    public int peerPort;

    public boolean inputShutdown = false;
    public boolean outputShutdown = false;

    public boolean closed = false;
}
