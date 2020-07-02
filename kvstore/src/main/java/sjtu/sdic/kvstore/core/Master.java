package sjtu.sdic.kvstore.core;

import com.alipay.sofa.rpc.config.ProviderConfig;
import com.alipay.sofa.rpc.config.ServerConfig;
import org.I0Itec.zkclient.ZkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sjtu.sdic.kvstore.common.DistributedLock;
import sjtu.sdic.kvstore.common.Utils;
import sjtu.sdic.kvstore.common.ZkPath;
import sjtu.sdic.kvstore.rpc.MasterRpcService;
import sjtu.sdic.kvstore.rpc.RpcCall;
import java.util.*;


public class Master implements MasterRpcService {
    public MasterInfo masterInfo;
    private final HashMap<String, Group> groupNameMap;
    private final HashMap<String, LinkedList<VirtualGroup>> groupVirtualMap;
    private final HashMap<String, NodeInfo> nodeNameMap;
    private ZkClient zkClient;
    private final Chord chord;
    private final Logger log = LoggerFactory.getLogger(Master.class);
    private ProviderConfig<MasterRpcService> providerConfig;
    private DistributedLock ds;
    private DistributedLock.ReaderWriterLock readerWriterLock;
    public Master() {
        groupVirtualMap = new HashMap<>();
        groupNameMap = new HashMap<>();
        nodeNameMap = new HashMap<>();
        masterInfo = Utils.loadConfig("master.yaml", MasterInfo.class);
        if (masterInfo == null || masterInfo.zookeeper == null) {
            log.error("No ZOOKEEPER information provided in master.yaml, you should specify <zookeeper:server_string> in master.yaml");
            exit();
        }
        chord = new Chord();

    }

    @Override
    public synchronized GroupHandle getNodes(String key) {
        Group group;
        GroupHandle handle = new GroupHandle();

        readerWriterLock.lockReader();

        group = chord.mapKey(key);
        if (group == null) {
            readerWriterLock.unlockReader();
            return null;
        }
        log.info("Receive request with key [{}], index: {},mapping to group[{}]", key, Utils.hash(key), group.name);
        handle.version = group.version;
        handle.primaryNode = group.getPrimaryNode();
        handle.nodes = group.nodes;

        readerWriterLock.unlockReader();
        return handle;
    }

    public void expose() {
        ServerConfig serverConfig = new ServerConfig()
                .setProtocol("bolt") // Set a protocol, which is bolt by default
                .setPort(masterInfo.port) // set a port, which is 13300 by default
                .setDaemon(true); // daemon thread

        providerConfig = new ProviderConfig<MasterRpcService>()
                .setInterfaceId(MasterRpcService.class.getName()) // Specify the interface
                .setRef(this) // Specify the implementation
                .setUniqueId(Utils.getMasterId(masterInfo))
                .setServer(serverConfig); // Specify the server

        providerConfig.export(); // Publish service
    }

    public void run() {
        configZk();
        expose();
        log.info("Master starts ok");
        // assume data nodes start after master
    }

    private void configZk() {
        if (zkClient == null)
            zkClient = new ZkClient(masterInfo.zookeeper, 10000);

        ds = new DistributedLock(zkClient);
        readerWriterLock = ds.readerWriter("/reader_writer");

        try {
            zkClient.createPersistent(ZkPath.NODE_ROOT);
        } catch (RuntimeException e) {
            log.warn("{} already exists", ZkPath.NODE_ROOT);
        }

        try {
            zkClient.createPersistent(ZkPath.LOCK_ROOT);
        } catch (RuntimeException e) {
            log.warn("{} already exists", ZkPath.LOCK_ROOT);
        }

        try {
            zkClient.createPersistent(ZkPath.MUTEX_LOCK_ROOT);
        } catch (RuntimeException e) {
            log.warn("{} already exists", ZkPath.MUTEX_LOCK_ROOT);
        }

        try {
            zkClient.createPersistent(ZkPath.READER_WRITER_LOCK_ROOT, 0);
        } catch (RuntimeException e) {
            try {
                zkClient.writeData(ZkPath.READER_WRITER_LOCK_ROOT, 0);
            } catch (RuntimeException e1){
                e1.printStackTrace();
            }
            log.warn("{} already exists", ZkPath.READER_WRITER_LOCK_ROOT);
        }

        try {
            zkClient.createPersistent(ZkPath.READER_WRITER_LOCK_ROOT + "/reader");
        } catch (RuntimeException e) {
            log.warn("{} already exists", ZkPath.READER_WRITER_LOCK_ROOT + "/reader");
        }

        try {
            zkClient.createPersistent(ZkPath.READER_WRITER_LOCK_ROOT + "/writer");
        } catch (RuntimeException e) {
            log.warn("{} already exists", ZkPath.READER_WRITER_LOCK_ROOT + "/writer");
        }

        while (true) {
            try{
                zkClient.createEphemeral(ZkPath.MASTER_ROOT, Utils.yamlToString(masterInfo));
                break;
            } catch (RuntimeException e) {
                log.warn("{} already exists, it may created by previous master, wait 300ms ...", ZkPath.MASTER_ROOT);
                try {
                    Thread.sleep(300);
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
            }
        }

        try {
            zkClient.subscribeChildChanges(ZkPath.NODE_ROOT, (parentPath, currentChilds) -> {
                log.warn("Nodes states changed");
                log.info("Lock the whole cluster");
                readerWriterLock.lockWriter();
                // add new nodes to nodeNameMap
                currentChilds.forEach(child -> {
                    if (nodeNameMap.get(child) == null) {
                        String data;
                        data = zkClient.readData(parentPath + "/" + child);
                        List<VirtualGroup> vgList = addNode(child, data);
                        vgList.forEach(this::migrateNodeData);
                    }
                });

                // check if there are some nodes lost connection
                checkNodesLoss(currentChilds);
                readerWriterLock.unlockWriter();
            });
        } catch (RuntimeException e) {
            e.printStackTrace();
            log.error("Subscribing nodes states failed");
            exit();
        }
    }

    public void exit() {
        if (zkClient != null)
            zkClient.close();
        System.exit(1);
    }

    private List<VirtualGroup> addNode(String name, String data) {
        NodeInfo n;
        Group g;
        VirtualGroup vg;
        List<VirtualGroup> vgList = new ArrayList<>();
        int i;

        n = Utils.stringToYaml(data, NodeInfo.class);
        nodeNameMap.put(name, n);
        g = groupNameMap.get(n.group);
        if (g != null) {
            g.nodes.add(n);
            log.info("Add new node {} to group[{}]", name, g.name);
            try {
                log.info("Synchronizing node {} state with {}", n.name, g.getPrimaryNode().name);
                RpcCall.getNodeRpcService(n).sync(g.getPrimaryNode());
                log.info("Synchronized node [{}] with node [{}]", n.name, g.getPrimaryNode().name);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
        else {
            g = new Group(n.group, n.weight);
            g.nodes.add(n);

            groupNameMap.put(n.group, g);
            log.info("Add new group[{}] to cluster", g.name);
            log.info("Add new node {} to group[{}]", name, g.name);
            groupVirtualMap.put(g.name, new LinkedList<>());
            log.info("Add new mapping from {} to virtualGroup", g.name);
            for (i = 0; i < g.weight; i++) {
                vg = new VirtualGroup(
                        Utils.hash(Utils.genVirtualGroupName(g.name, i)),
                        Utils.genVirtualGroupName(g.name, i),
                        g);
                chord.addGroup(vg);
                groupVirtualMap.get(g.name).add(vg);
                vgList.add(vg);
            }
        }
        return vgList;
    }

    private void checkNodesLoss(List<String> nodes) {
        Iterator<String> it;
        NodeInfo n;
        Group g;
        String k;

        it = nodeNameMap.keySet().iterator();
        while (it.hasNext()) {
            k = it.next();
            // Node Failure
            if (!nodes.contains(k)) {
                log.info("Node {} connection lost, removing it...", k);
                n = nodeNameMap.get(k);
                g = groupNameMap.get(n.group);
                g.nodes.remove(n);
                log.info("Removed node {} from group {}", n.name, g.name);
                if (g.nodes.isEmpty()) {
                    log.info("group {} empty, removing it ...", g.name);
                    if (groupVirtualMap.get(g.name) == null) {
                        log.error("groupVirtualMap[{}] is null, groupVirtualMap size is {}", g.name, groupVirtualMap.size());
                    }
                    for(VirtualGroup virtualGroup : groupVirtualMap.get(g.name)) {
                        chord.removeGroup(virtualGroup);
                    }
                    groupNameMap.remove(n.group);
                    groupVirtualMap.remove(g.name);
                }
                it.remove();
                log.info("Removed node {} in master", n.name);
            }
        }
    }

    private void migrateNodeData(VirtualGroup dst) {
        int start, end;
        VirtualGroup src, prev;
        boolean r;

        src = chord.getNextGroup(dst);

        while (src.group == dst.group) {
            src = chord.getNextGroup(src);
            if (src == dst)
                return;
        }

        prev = chord.getPrevGroup(dst);
        start = prev.index + 1; // exclude index of previous group
        end = dst.index + 1;    // include index of dst group

        // this should be the case that dst is the first group, while src is the last group in the ring
        if (start > end) {
            log.info("Migrating data between {} and {}, src is the first Virtual Group in the ring", src.name, dst.name);
            try {
                r = RpcCall.getNodeRpcService(
                        src.group.getPrimaryNode()).migrateData(
                        start, Integer.MAX_VALUE,
                        new GroupHandle(src.group.version, src.group.getPrimaryNode(), src.group.nodes),
                        new GroupHandle(dst.group.version, dst.group.getPrimaryNode(), dst.group.nodes));
                if (!r) {
                    log.warn("Migrate Data returns false between {} and {}", src.group.name, dst.group.name);
                }
                r = RpcCall.getNodeRpcService(
                        src.group.getPrimaryNode()).migrateData(
                        0, end,
                        new GroupHandle(src.group.version, src.group.getPrimaryNode(), src.group.nodes),
                        new GroupHandle(dst.group.version, dst.group.getPrimaryNode(), dst.group.nodes));
                if (!r) {
                    log.warn("Migrate Data returns false between {} and {}", src.group.name, dst.group.name);
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        } else {
            log.info("Migrating data between {} and {}", start, end);
            try {
                r = RpcCall.getNodeRpcService(
                        src.group.getPrimaryNode()).migrateData(
                        start, end,
                        new GroupHandle(src.group.version, src.group.getPrimaryNode(), src.group.nodes),
                        new GroupHandle(dst.group.version, dst.group.getPrimaryNode(), dst.group.nodes));
                if (!r) {
                    log.warn("Migrate Data returns false between {} and {}", src.group.name, dst.group.name);
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }
}
