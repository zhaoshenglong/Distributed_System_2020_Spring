package sjtu.sdic.kvstore.core;
import com.alipay.sofa.rpc.config.ProviderConfig;
import com.alipay.sofa.rpc.config.ServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.I0Itec.zkclient.ZkClient;
import sjtu.sdic.kvstore.common.DistributedLock;
import sjtu.sdic.kvstore.common.LogOps;
import sjtu.sdic.kvstore.common.Utils;
import sjtu.sdic.kvstore.common.ZkPath;
import sjtu.sdic.kvstore.rpc.NodeRpcService;
import sjtu.sdic.kvstore.rpc.RpcCall;

import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

@Slf4j
public class Node implements NodeRpcService {
    private final ConcurrentSkipListMap<Integer, ConcurrentHashMap<String, String>> data;
    private NodeInfo nodeInfo;
    private ZkClient zkClient;
    private ProviderConfig<NodeRpcService> providerConfig;
    private int version;
    private DistributedLock ds;
    private DistributedLock.ReaderWriterLock readerWriterLock;
    private NodeLogger nodeLogger;

    public Node(String configFile) {
        nodeInfo = Utils.loadConfig(configFile, NodeInfo.class);
        data = new ConcurrentSkipListMap<>();
        if (nodeInfo == null || nodeInfo.zookeeper == null) {
            log.error("No ZOOKEEPER information provided in master.yaml, you should specify <zookeeper:server_string> in master.yaml");
            exit();
        }
        nodeLogger = new NodeLogger(nodeInfo.name + ".log", nodeInfo.name + ".ck");

        // 读取本地 log
        data.putAll(nodeLogger.restore());
    }

    /**
     *
     * @param key - String
     * @param handle - String
     * @return NodeResponse
     *                  RES_TYPE, value
     */
    @Override
    public synchronized NodeResponse delete(String key, GroupHandle handle) {
        NodeResponse res, r;
        String val = null;
        if (Utils.getNodeId(handle.primaryNode).equals(Utils.getNodeId(nodeInfo))) {
            readerWriterLock.lockReader();
        }

        log.info("Deleting key {}", key);
        nodeLogger.append(LogOps.DELETE, key, "");

        if (data.get(Utils.hash(key)) != null) {
            val = data.get(Utils.hash(key)).remove(key);
        }
        res = new NodeResponse(RES_TYPE.SUCCESS, val);

        if (Utils.getNodeId(handle.primaryNode).equals(Utils.getNodeId(nodeInfo))) {
            for (NodeInfo node: handle.nodes) {
                if (!Utils.getNodeId(node).equals(Utils.getNodeId(handle.primaryNode))) {
                    try {
                        r = RpcCall.getNodeRpcService(node).delete(key, handle);
                        if (r.payload != null && !r.payload.equals(res.payload)) {
                            log.error("Node {} and node {} deletion inconsistency, {} deleted {}, {} deleted {}",
                                    nodeInfo.name, node.name, nodeInfo.name, res.payload, node.name, r.payload);
                            res.resType = RES_TYPE.ERROR;
                            res.payload = "Deletion inconsistent";
                            readerWriterLock.unlockReader();
                            return res;
                        }
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                        res.resType = RES_TYPE.ERROR;
                        res.payload = String.format("Backup node[%s] failed deletion operation", node.name);
                        readerWriterLock.unlockReader();
                        return res;
                    }

                }
            }
            readerWriterLock.unlockReader();
        }
        return res;
    }

    /**
     *
     * @param key - String
     * @param value - String
     * @param handle - GroupHandle granted from Master
     * @return NodeResponse
     *                  RES_TYPE, value
     */
    @Override
    public synchronized NodeResponse put(String key, String value, GroupHandle handle) {
        NodeResponse res, r;

        if (Utils.getNodeId(handle.primaryNode).equals(Utils.getNodeId(nodeInfo))) {
            readerWriterLock.lockReader();
        }

        log.info("Putting key {}, value {}", key, value);
        nodeLogger.append(LogOps.PUT, key, value);

        data.computeIfAbsent(Utils.hash(key), k -> data.put(k, new ConcurrentHashMap<>()));
        data.get(Utils.hash(key)).put(key, value);

        res = new NodeResponse(RES_TYPE.SUCCESS, value);
        if (Utils.getNodeId(handle.primaryNode).equals(Utils.getNodeId(nodeInfo))) {
            for (NodeInfo node: handle.nodes) {
                if (!Utils.getNodeId(node).equals(Utils.getNodeId(handle.primaryNode))) {
                    try {
                        r = RpcCall.getNodeRpcService(node).put(key, value, handle);
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                        res.resType = RES_TYPE.ERROR;
                        res.payload = String.format("Backup node[%s] failed put operation", node.name);
                        readerWriterLock.unlockReader();
                        return res;
                    }
                }
            }
            readerWriterLock.unlockReader();
        }
        return res;
    }

    @Override
    public NodeResponse get(String key, GroupHandle handle) {
        NodeResponse res = null;

        log.info("getting key {}", key);
        if(key != null){
            ConcurrentHashMap<String, String> m = data.get(Utils.hash(key));
            if (m != null)
                res = new NodeResponse(RES_TYPE.SUCCESS, m.get(key));
        }
        if (res == null)
            res = new NodeResponse(RES_TYPE.SUCCESS, null);
        return res;
    }

    @Override
    public SortedMap<Integer, ConcurrentHashMap<String, String>> getAll() {
        return data;
    }

    @Override
    public synchronized SortedMap<Integer, ConcurrentHashMap<String, String>> removeRange(int low, int up) {
        SortedMap<Integer, ConcurrentHashMap<String, String>> res, subData;

        subData = data.subMap(low, up);
        res = new ConcurrentSkipListMap<>(subData);
        subData.clear();
        nodeLogger.writeCheckpoint(data);
        nodeLogger.append(LogOps.CHECKPOINT, "", "");
        return res;
    }

    @Override
    public boolean putRange(SortedMap<Integer, ConcurrentHashMap<String, String>> m, GroupHandle handle) {
        boolean r;


        data.putAll(m);
        nodeLogger.writeCheckpoint(data);
        nodeLogger.append(LogOps.CHECKPOINT, "", "");

        if (Utils.getNodeId(handle.primaryNode).equals(Utils.getNodeId(nodeInfo))) {
            for (NodeInfo node: handle.nodes) {
                if (!Utils.getNodeId(node).equals(Utils.getNodeId(handle.primaryNode))) {
                    try {
                        r = RpcCall.getNodeRpcService(node).putRange(m, handle);
                        if (!r)
                            return false;
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean migrateData(int low, int up, GroupHandle srcHandle, GroupHandle dstHandle) {
        SortedMap<Integer, ConcurrentHashMap<String, String>> m, r;

        if (Utils.getNodeId(srcHandle.primaryNode).equals(Utils.getNodeId(nodeInfo))) {
            log.info("Removing data whose index between {} and {}", low, up);
            r = removeRange(low, up);
            for (NodeInfo node: srcHandle.nodes) {
                if (!Utils.getNodeId(node).equals(Utils.getNodeId(srcHandle.primaryNode))) {
                    try {
                        m = RpcCall.getNodeRpcService(node).removeRange(low, up);
                        if (!r.keySet().containsAll(m.keySet())) {
                            log.error("Node data inconsistency");
                        }
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                        return false;
                    }
                }
            }
            try {
                log.info("Putting key value pair to another group[{}]", dstHandle.primaryNode.group);
                for (int i:r.keySet()) {
                    r.get(i).forEach((k, v) -> {
                        System.out.println(k + "----->" + v);
                    });
                }
                RpcCall.getNodeRpcService(dstHandle.primaryNode).putRange(r, dstHandle);
                return true;
            } catch (RuntimeException e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    @Override
    public synchronized boolean sync(NodeInfo srcNode) {
        SortedMap<Integer, ConcurrentHashMap<String, String >> map;
        try {
            map = RpcCall.getNodeRpcService(srcNode).getAll();

            nodeLogger.writeCheckpoint(map);
            nodeLogger.append(LogOps.CHECKPOINT, "", "");
            data.clear();
            data.putAll(map);
            return true;
        } catch (RuntimeException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void run() {
        register();
        expose();
    }

    private void exit() {
        System.exit(1);
    }

    private void register() {
        zkClient = new ZkClient(nodeInfo.zookeeper, 3000);

        ds = new DistributedLock(zkClient);
        readerWriterLock = ds.readerWriter("/reader_writer");

        while (true) {
            try {
                zkClient.createEphemeral(ZkPath.NODE_ROOT + "/" + nodeInfo.name, Utils.yamlToString(nodeInfo));
                break;
            } catch (RuntimeException e) {
                log.warn("{} already exists, it may created by previous master, wait 300ms ...", nodeInfo.name);
                try {
                    Thread.sleep(300);
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
            }
        }
    }

    private void expose() {
        ServerConfig serverConfig = new ServerConfig()
                .setProtocol("bolt") // Set a protocol, which is bolt by default
                .setPort(nodeInfo.port) // set a port, which is 13300 by default
                .setDaemon(true); // daemon thread

        providerConfig = new ProviderConfig<NodeRpcService>()
                .setInterfaceId(NodeRpcService.class.getName()) // Specify the interface
                .setRef(this) // Specify the implementation
                .setUniqueId(Utils.getNodeId(nodeInfo))
                .setServer(serverConfig); // Specify the server

        providerConfig.export(); // Publish service
    }
}
