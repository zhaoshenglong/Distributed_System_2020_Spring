package sjtu.sdic.kvstore.core;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import sjtu.sdic.kvstore.common.Utils;

import java.util.LinkedList;
import java.util.NoSuchElementException;

@EqualsAndHashCode
@Data
@NoArgsConstructor
public class Group {
    public String name;
    public int weight;
    public LinkedList<NodeInfo> nodes;
    public int version;

    public Group(String name, int weight) {
        this.name = name;
        this.weight = weight;
        this.nodes = new LinkedList<>();
        this.version = Utils.randomInt();
    }

    public void updateState() {
        this.version++;
    }

    public NodeInfo getPrimaryNode() {
        try {
            return nodes.getFirst();
        } catch (NoSuchElementException e) {
            e.printStackTrace();
            return null;
        }
    }
}
