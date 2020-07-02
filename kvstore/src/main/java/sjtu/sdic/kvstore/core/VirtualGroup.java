package sjtu.sdic.kvstore.core;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class VirtualGroup {
    public int index;
    public String name;
    public Group group;

    public VirtualGroup(int index, String name, Group group) {
        this.index = index;
        this.name = name;
        this.group = group;
    }
}
