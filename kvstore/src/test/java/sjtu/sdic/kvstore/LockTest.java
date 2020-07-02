package sjtu.sdic.kvstore;

import org.I0Itec.zkclient.ZkClient;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import sjtu.sdic.kvstore.common.DistributedLock;
import sjtu.sdic.kvstore.common.ZkPath;

import java.util.ArrayList;
import java.util.List;

public class LockTest {
    private int cnt;

    @Before
    public void init() {
        cnt = 0;
    }

    @Test
    public void testMutexLock() {
        int nThreads = 5, i;
        List<Thread> threadList = new ArrayList<>();
        int eachCnt = 10;

        for (i = 0; i < nThreads; i++) {
            Thread thread = new Thread(() -> {
                ZkClient zkClient = new ZkClient("localhost:2181,localhost:2182,localhost:2183", 3000);
                DistributedLock ds = new DistributedLock(zkClient);
                DistributedLock.Mutex mutex = ds.mutex();
                for (int j = 0; j < eachCnt; j++) {
                    mutex.lock("/mutex");
                    cnt++;
                    mutex.unlock("/mutex");
                }
            });
            threadList.add(thread);
            thread.start();
        }

        for (Thread t: threadList){
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        Assert.assertEquals(nThreads * eachCnt, cnt);
    }

    @Test
    public void testReaderWriterLock() {
        int nReaderThreads = 10,
                nWriterThreads = 5,
                i;
        List<Thread> threadList = new ArrayList<>();

        // remove log
        Logger.getRootLogger().setLevel(Level.OFF);

        ZkClient zk = new ZkClient("localhost:2181,localhost:2182,localhost:2183", 3000);
        try {
            zk.writeData(ZkPath.READER_WRITER_LOCK_ROOT, 0);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        // Reader threads
        for (i = 0; i < nReaderThreads; i++) {
            int finalI = i;
            Thread thread = new Thread(() -> {
                ZkClient zkClient = new ZkClient("localhost:2181,localhost:2182,localhost:2183", 3000);
                DistributedLock ds = new DistributedLock(zkClient);
                DistributedLock.ReaderWriterLock readerWriterLock = ds.readerWriter("/reader_writer");
                readerWriterLock.lockReader();
                TestUtils.doReader(finalI);
                readerWriterLock.unlockReader();
            });
            threadList.add(thread);
            thread.start();
        }

        // Writer threads
        for (i = 0; i < nWriterThreads; i++) {
            int finalI1 = i;
            Thread thread = new Thread(() -> {
                ZkClient zkClient = new ZkClient("localhost:2181,localhost:2182,localhost:2183", 3000);
                DistributedLock ds = new DistributedLock(zkClient);
                DistributedLock.ReaderWriterLock readerWriterLock = ds.readerWriter("/reader_writer");
                readerWriterLock.lockWriter();
                TestUtils.doWriter(finalI1);
                readerWriterLock.unlockWriter();
            });
            threadList.add(thread);
            thread.start();
        }

        // Wait for all threads completed
        for (Thread t: threadList){
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
