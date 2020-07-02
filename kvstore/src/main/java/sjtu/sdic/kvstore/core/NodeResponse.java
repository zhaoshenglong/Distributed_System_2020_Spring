package sjtu.sdic.kvstore.core;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;


@NoArgsConstructor
@AllArgsConstructor
public class NodeResponse {
    public RES_TYPE resType;
    public String payload;
}
