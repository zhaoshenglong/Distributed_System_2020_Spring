package sjtu.sdic.kvstore.rpc;

import sjtu.sdic.kvstore.core.GroupHandle;


public interface MasterRpcService {
    GroupHandle getNodes(String key);
}
