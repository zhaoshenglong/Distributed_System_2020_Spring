package sjtu.sdic.kvstore;

import org.junit.Assert;
import org.junit.Test;
import sjtu.sdic.kvstore.common.Utils;
import sjtu.sdic.kvstore.core.GroupHandle;
import sjtu.sdic.kvstore.core.MasterInfo;
import sjtu.sdic.kvstore.core.NodeResponse;
import sjtu.sdic.kvstore.rpc.RpcCall;

import java.util.ArrayList;
import java.util.List;

public class AppTest {
    MasterInfo master = new MasterInfo("localhost", 1211);

    private void doRequest(int nThreads, int reqPerThread) {
        int i;
        Thread t;
        List<Thread> threadList = new ArrayList<>();

        for (i = 0; i < nThreads; i++) {
            int finalI = i;
            t = new Thread(() -> {
                GroupHandle handle;
                String key, value;
                NodeResponse res;
                int keyLen = finalI + 32;
                for (int j = 0; j < reqPerThread; j++) {
                    key = Utils.randomKeyGen(keyLen);
                    value = Utils.randomKeyGen(32);
                    try {
                        handle = RpcCall.getMasterRpcService(master).getNodes(key);
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                        continue;
                    }
                    try {
                        res = RpcCall.getNodeRpcService(handle.primaryNode).put(key, value, handle);
                        res = RpcCall.getNodeRpcService(handle.primaryNode).get(key, handle);
                        Assert.assertEquals(res.payload, value);
                        res = RpcCall.getNodeRpcService(handle.primaryNode).delete(key, handle);
                        Assert.assertEquals(res.payload, value);
                        res = RpcCall.getNodeRpcService(handle.primaryNode).get(key, handle);
                        Assert.assertNull(res.payload);
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                    }
                }
            });
            threadList.add(t);
            t.start();
        }
        for (Thread thread: threadList) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testSingleClient() {
        doRequest(1, 20);
    }

    @Test
    public void testMultiClient() {
        doRequest(3, 20);
    }
}
