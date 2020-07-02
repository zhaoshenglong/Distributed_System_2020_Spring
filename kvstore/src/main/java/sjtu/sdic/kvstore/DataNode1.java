package sjtu.sdic.kvstore;

import sjtu.sdic.kvstore.core.Node;

/**
 * Hello world!
 *
 */
public class DataNode1
{
    public static void main( String[] args ) {
        Node node = new Node("datanode1.yaml");
        try {
            node.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
