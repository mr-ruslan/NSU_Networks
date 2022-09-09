import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TimerTask;

public class Viewer extends TimerTask {
    private Map<InetSocketAddress, Integer> active;
    private boolean changed = false;
    private static final int ttl = 2;
    Viewer(){
        active = new HashMap<>();
    }

    @Override
    synchronized public void run() {
        for (Iterator<Map.Entry<InetSocketAddress, Integer>> it = active.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<InetSocketAddress, Integer> entry = it.next();
            if (entry.getValue() > 0){
                active.put(entry.getKey(), entry.getValue() - 1);
            }
            else{
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            System.out.println("Currently alive:");
            for (Map.Entry<InetSocketAddress, Integer> entry : active.entrySet()) {
                System.out.println(entry.getKey().getHostName() + " - " + entry.getKey().getAddress() + ":" + entry.getKey().getPort());
            }
            System.out.println("--------------------------------------------------------------------------------");
            changed = false;
        }

    }

    synchronized public void alive(InetSocketAddress address){
        if (!active.containsKey(address)){
            changed = true;
        }
        active.put(address, ttl);
    }


}
