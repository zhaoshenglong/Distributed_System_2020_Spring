package sjtu.sdic.kvstore;


import sjtu.sdic.kvstore.core.Master;
import java.io.FileNotFoundException;

public class MasterNode {
    public static void main( String[] args ) throws FileNotFoundException {
        Master master = new Master();
        try {
            master.run();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }
}
