package sjtu.sdic.kvstore.rpc;

import sjtu.sdic.kvstore.core.Group;
import sjtu.sdic.kvstore.core.GroupHandle;
import sjtu.sdic.kvstore.core.NodeInfo;
import sjtu.sdic.kvstore.core.NodeResponse;

import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;

public interface NodeRpcService {
    NodeResponse delete(String key, GroupHandle handle);

    NodeResponse put(String key, String value, GroupHandle handle);

    NodeResponse get(String key, GroupHandle handle);

    SortedMap<Integer, ConcurrentHashMap<String, String>> getAll();

    SortedMap<Integer, ConcurrentHashMap<String, String>> removeRange(int low, int up);

    boolean putRange(SortedMap<Integer, ConcurrentHashMap<String, String>> m, GroupHandle handle);

    boolean migrateData(int low, int up, GroupHandle srcHandle, GroupHandle dstHandle);

    boolean sync(NodeInfo srcNode);
}
