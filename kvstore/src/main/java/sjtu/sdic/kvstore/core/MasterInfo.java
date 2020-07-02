package sjtu.sdic.kvstore.core;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MasterInfo {
    public String address;
    public int port;
    public String zookeeper;

    public MasterInfo(String address, int port) {
        this.address = address;
        this.port = port;
    }
}
