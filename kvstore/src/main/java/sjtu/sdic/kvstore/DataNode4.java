package sjtu.sdic.kvstore;

import sjtu.sdic.kvstore.core.Node;

/**
 * Hello world!
 *
 */
public class DataNode4
{
    public static void main( String[] args ) {
        Node node = new Node("datanode4.yaml");
        try {
            node.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
