package sjtu.sdic.kvstore;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import sjtu.sdic.kvstore.common.LogOps;
import sjtu.sdic.kvstore.common.Utils;
import sjtu.sdic.kvstore.core.NodeLogger;

import java.io.File;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;


public class NodeLoggerTest {
    private NodeLogger nodeLogger;

    @Before
    public void init() {
        File logFile = new File("testNode.log"),
            ckFile = new File("testNode.ck");

        logFile.delete();
        ckFile.delete();
        nodeLogger = new NodeLogger("testNode.log", "testNode.ck");
    }

    @Test
    public void testSimpleLog() {
        SortedMap<Integer, ConcurrentHashMap<String, String>> map;

        nodeLogger.append(LogOps.PUT, "1", "1");
        nodeLogger.append(LogOps.PUT, "2", "2");
        nodeLogger.append(LogOps.PUT, "3", "3");
        nodeLogger.append(LogOps.DELETE, "1", "1");

        map = nodeLogger.restore();
        Assert.assertEquals(3, map.size());
        Assert.assertEquals("2", map.get(Utils.hash("2")).get("2"));
        Assert.assertEquals("3", map.get(Utils.hash("3")).get("3"));
    }

    @Test
    public void testCheckpoint() {
        SortedMap<Integer, ConcurrentHashMap<String, String>> map;

        nodeLogger.append(LogOps.PUT, "1", "1");
        nodeLogger.append(LogOps.PUT, "2", "2");
        nodeLogger.append(LogOps.PUT, "3", "3");
        nodeLogger.append(LogOps.DELETE, "1", "1");

        map = nodeLogger.restore();

        nodeLogger.writeCheckpoint(map);
        map = nodeLogger.readCheckpoint();
        Assert.assertEquals(3, map.size());
        Assert.assertEquals("2", map.get(Utils.hash("2")).get("2"));
        Assert.assertEquals("3", map.get(Utils.hash("3")).get("3"));
    }

    @Test
    public void testCheckpointWithNoFile() {
        SortedMap<Integer, ConcurrentHashMap<String, String>> map;
        map = nodeLogger.readCheckpoint();
        Assert.assertEquals(0, map.size());
    }

    @Test
    public void testLogWithNoFile() {
        SortedMap<Integer, ConcurrentHashMap<String, String>> map;
        map = nodeLogger.restore();
        Assert.assertEquals(0, map.size());
    }
}
