package sjtu.sdic.kvstore;

import lombok.extern.slf4j.Slf4j;
import org.I0Itec.zkclient.ZkClient;
import org.apache.log4j.BasicConfigurator;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import sjtu.sdic.kvstore.common.DistributedLock;
import sjtu.sdic.kvstore.common.Utils;
import sjtu.sdic.kvstore.core.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Unit test for simple App.
 */
@Slf4j
public class MasterTest
{
    private Chord chord;

    @Before
    public void init() {
        chord = new Chord();
    }

    @Test
    public void testChord() {
        Group group1 = new Group("group-1", 3),
                group2 = new Group("group-2", 3);
        int i;
        int g1_cnt = 0, g2_cnt = 0;
        String key;
        VirtualGroup vg;
        HashMap<Group, LinkedList<VirtualGroup>> gm = new HashMap<>();
        gm.put(group1, new LinkedList<>());
        gm.put(group2, new LinkedList<>());

        for(i = 0; i < group1.weight; i++) {
            vg = new VirtualGroup(Utils.hash(Utils.genVirtualGroupName(group1.name, i)),
                    Utils.genVirtualGroupName(group1.name, i), group1);
            gm.get(group1).add(vg);
            chord.addGroup(vg);
        }
        for (i = 0; i < 1000; i++) {
            Assert.assertEquals(group1, chord.mapKey(Utils.randomKeyGen(32)));
        }
        for(i = 0; i < group2.weight; i++) {
            vg = new VirtualGroup(Utils.hash(Utils.genVirtualGroupName(group2.name, i)),
                    Utils.genVirtualGroupName(group2.name, i), group2);
            gm.get(group2).add(vg);
            chord.addGroup(vg);
        }

        for (i = 0; i < 1000; i++) {
            key = Utils.randomKeyGen(32);
            if (group1 == chord.mapKey(key))
                g1_cnt++;
            else if (group2 == chord.mapKey(key))
                g2_cnt++;
            else
                log.error("chord.mapKey returned a non-exist group");
        }
        Assert.assertEquals(1000,g1_cnt + g2_cnt);
        System.out.println("g1_cnt: " + g1_cnt + " , g2_cnt: " + g2_cnt);

        gm.get(group1).forEach(v -> Assert.assertEquals(v, chord.getNextGroup(
                chord.getPrevGroup(v))));
        gm.get(group2).forEach(v -> Assert.assertEquals(v, chord.getNextGroup(
                chord.getPrevGroup(v))));

        gm.get(group1).forEach(v -> Assert.assertEquals(v,
                chord.getPrevGroup(
                    chord.getPrevGroup(
                            chord.getPrevGroup(
                                    chord.getPrevGroup(
                                            chord.getPrevGroup(
                                                chord.getPrevGroup(v))))))));

        gm.get(group2).forEach(v -> Assert.assertEquals(v,
                chord.getPrevGroup(
                        chord.getPrevGroup(
                                chord.getPrevGroup(
                                        chord.getPrevGroup(
                                                chord.getPrevGroup(
                                                        chord.getPrevGroup(v))))))));
        gm.get(group1).forEach(v -> Assert.assertEquals(v,
                chord.getNextGroup(
                        chord.getNextGroup(
                                chord.getNextGroup(
                                        chord.getNextGroup(
                                                chord.getNextGroup(
                                                        chord.getNextGroup(v))))))));
        gm.get(group2).forEach(v -> Assert.assertEquals(v,
                chord.getNextGroup(
                        chord.getNextGroup(
                                chord.getNextGroup(
                                        chord.getNextGroup(
                                                chord.getNextGroup(
                                                        chord.getNextGroup(v))))))));
        chord.removeGroup(gm.get(group1).getFirst());
        gm.get(group2).forEach(v -> Assert.assertEquals(v,
                chord.getNextGroup(
                        chord.getNextGroup(
                                chord.getNextGroup(
                                        chord.getNextGroup(
                                                chord.getNextGroup(v)))))));
        vg = gm.get(group1).getFirst();
        for (i = 0; i < 6; i++) {
            System.out.println(vg.name + " " + vg.index);
            vg = chord.getNextGroup(vg);
        }
    }

    /**
     * Master Unit test，running this test requires comment
     *  <code>readerWriter.lockReader</code> and <code>readerWriter.unlockReader</code> in the Master
     */
    @Test
    public void testMaster() {
        Master master = new Master();
        GroupHandle handle;

        // master private field
        HashMap<String, Group> groupNameMap = null;
        HashMap<Group, LinkedList<VirtualGroup>> groupVirtualMap = null;
        HashMap<String, NodeInfo> nodeNameMap = null;

        Map<String, NodeInfo> nodes = new HashMap<>();
        NodeInfo nodeInfo;
        nodeInfo = new NodeInfo("localhost", 12111, "node-1", "zk", "group-1", 3);
        nodes.put(nodeInfo.name, nodeInfo);
        nodeInfo = new NodeInfo("localhost", 12112, "node-2", "zk", "group-1", 3);
        nodes.put(nodeInfo.name, nodeInfo);
        nodeInfo = new NodeInfo("localhost", 12113, "node-3", "zk", "group-2", 3);
        nodes.put(nodeInfo.name, nodeInfo);
        nodeInfo = new NodeInfo("localhost", 12114, "node-4", "zk", "group-2", 3);
        nodes.put(nodeInfo.name, nodeInfo);

        // test master with empty nodes
        handle = master.getNodes(Utils.randomKeyGen(10));
        Assert.assertNull(handle);


        // test add one node
        try {
            TestUtils.getMethod(Master.class.getName(), "addNode", String.class, String.class)
                    .invoke(master, "node-1", Utils.yamlToString(nodes.get("node-1")));
            groupNameMap = (HashMap<String, Group>) TestUtils.getField(Master.class.getName(), "groupNameMap").get(master);
            groupVirtualMap = (HashMap<Group, LinkedList<VirtualGroup>>) TestUtils.getField(Master.class.getName(), "groupVirtualMap").get(master);
            nodeNameMap = (HashMap<String, NodeInfo>) TestUtils.getField(Master.class.getName(), "nodeNameMap").get(master);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        Assert.assertNotNull(master.getNodes(Utils.randomKeyGen(32)));
        Assert.assertEquals(nodes.get("node-1"), master.getNodes(Utils.randomKeyGen(32)).primaryNode);
        Assert.assertEquals(1, master.getNodes(Utils.randomKeyGen(32)).nodes.size());
        Assert.assertEquals(1, groupNameMap.keySet().size());
        Assert.assertEquals(1, groupVirtualMap.keySet().size());
        Assert.assertEquals(1, nodeNameMap.entrySet().size());

        // test add another node
        try {
            TestUtils.getMethod(Master.class.getName(), "addNode", String.class, String.class)
                    .invoke(master, "node-2", Utils.yamlToString(nodes.get("node-2")));
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        Assert.assertNotNull(master.getNodes(Utils.randomKeyGen(32)));
        Assert.assertEquals(nodes.get("node-1"), master.getNodes(Utils.randomKeyGen(32)).primaryNode);
        Assert.assertEquals(2, master.getNodes(Utils.randomKeyGen(32)).nodes.size());
        Assert.assertEquals(1, groupNameMap.keySet().size());
        Assert.assertEquals(1, groupVirtualMap.keySet().size());
        Assert.assertEquals(2, nodeNameMap.entrySet().size());

        // test remove one node
        try {
            TestUtils.getMethod(Master.class.getName(), "checkNodesLoss", List.class)
                    .invoke(master, Arrays.asList("node-1"));
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        Assert.assertNotNull(master.getNodes(Utils.randomKeyGen(32)));
        Assert.assertEquals(nodes.get("node-1"), master.getNodes(Utils.randomKeyGen(32)).primaryNode);
        Assert.assertEquals(1, master.getNodes(Utils.randomKeyGen(32)).nodes.size());
        Assert.assertEquals(1, groupNameMap.keySet().size());
        Assert.assertEquals(1, groupVirtualMap.keySet().size());
        Assert.assertEquals(1, nodeNameMap.entrySet().size());

        // test add another group
        try {
            TestUtils.getMethod(Master.class.getName(), "addNode", String.class, String.class)
                    .invoke(master, "node-3", Utils.yamlToString(nodes.get("node-3")));
            TestUtils.getMethod(Master.class.getName(), "addNode", String.class, String.class)
                    .invoke(master, "node-4", Utils.yamlToString(nodes.get("node-4")));
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        Assert.assertEquals(2, groupNameMap.keySet().size());
        Assert.assertEquals(2, groupVirtualMap.keySet().size());
        Assert.assertEquals(3, nodeNameMap.entrySet().size());

    }
}
