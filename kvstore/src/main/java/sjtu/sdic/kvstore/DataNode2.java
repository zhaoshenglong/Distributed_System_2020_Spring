package sjtu.sdic.kvstore;

import sjtu.sdic.kvstore.core.Node;

/**
 * Hello world!
 *
 */
public class DataNode2
{
    public static void main( String[] args ) {
        Node node = new Node("datanode2.yaml");
        try {
            node.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
