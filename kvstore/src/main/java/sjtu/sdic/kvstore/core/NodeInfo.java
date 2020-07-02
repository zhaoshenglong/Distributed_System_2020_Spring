package sjtu.sdic.kvstore.core;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeInfo {
    public String address;
    public int port;
    public String name;
    public String zookeeper;

    // which group this node belongs to
    public String group;

    // weight of the group in hash ring
    public int weight;

    public NodeInfo(String name, String address, int port) {
        this.name = name;
        this.address = address;
        this.port = port;
    }
}
