package sjtu.sdic.kvstore.common;


import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import java.util.List;

public class DistributedLock {

    ZkClient zkClient;

    public DistributedLock(ZkClient zkClient) {
        this.zkClient = zkClient;
    }

    public Mutex mutex() {
        return new Mutex();
    }

    public class Mutex {
        boolean hasLock = false;
        String lockNode = "";

        /**
         * Acquire a distributed mutex lock
         * @param mutexLockPath - path for creating node. NOTE: This is not the final path for creating node,
         *                      the final path would be in the form LOCK_ROOT + mutexLockPath + "/nodexxxxxxxxxxx"
         *                      mutexLockPath should be in the form of `/xxxx`. In other words, it should start with /, and
         *                      should not end with /.
         */
        public void lock(String mutexLockPath) {
            // Acquire global lock
            String nodePath;
            String prevNodePath;
            List<String> nodes;

            // Trust lock users that whenever users calls lock, the mutex must have been unlocked
            hasLock = false;

            nodePath = zkClient.createEphemeralSequential(ZkPath.LOCK_ROOT + mutexLockPath + "/node", "");
            lockNode = nodePath.split("/")[nodePath.split("/").length - 1];

            nodes = zkClient.getChildren(ZkPath.LOCK_ROOT + mutexLockPath);
            nodes.sort(String::compareTo);
            if (lockNode.equals(nodes.get(0))) {
                hasLock = true;
                return;
            }

            prevNodePath = ZkPath.LOCK_ROOT + mutexLockPath + "/" + nodes.get(nodes.indexOf(lockNode) - 1);

            try {
                zkClient.subscribeDataChanges(prevNodePath, new IZkDataListener() {
                    @Override
                    public void handleDataChange(String dataPath, Object data) throws Exception {
                        //...
                    }

                    @Override
                    public void handleDataDeleted(String dataPath) throws Exception {
                        //...
                        hasLock = true;
                    }
                });
            } catch (RuntimeException e) {
                hasLock = true;
                if(zkClient.exists(prevNodePath)) {
                    e.printStackTrace();
                    hasLock = false;
                }
                return;
            }
            while (!hasLock) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * Release the distributed mutex lock
         * @param mutexLockPath - same as <code>mutexLock</code>
         */
        public void unlock(String mutexLockPath) {
            // Release global lock
            boolean r;
            String path;

            // 特殊处理 reader writer lock，如果 lockNode 为空（不是本人拿的锁，但是需要方所）

            path = ZkPath.LOCK_ROOT + mutexLockPath + "/" + lockNode;
            while (true) {
                try {
                    r = zkClient.delete(path);
                    if (r) {
                        hasLock = false;
                        return;
                    }
                } catch (RuntimeException e) {
                    System.out.println(path);
                    if (zkClient.exists(path)) {
                        e.printStackTrace();
                    } else {
                        hasLock = false;
                        return;
                    }
                }
            }
        }
    }

    public class ReaderWriterLock {
        String readerWriterPath;
        Mutex readerMutex;
        Mutex writerMutex;

        public ReaderWriterLock(String readerWriterPath) {
            this.readerWriterPath = readerWriterPath;
            readerMutex = mutex();
            writerMutex = mutex();
        }

        public void lockReader() {
            int readers;

            readerMutex.lock(readerWriterPath + "/reader");
            try {
                readers = zkClient.readData(ZkPath.LOCK_ROOT + readerWriterPath);
                if (readers == 0)
                    lockWriter();
                readers++;
                zkClient.writeData(ZkPath.LOCK_ROOT + readerWriterPath, readers);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
            readerMutex.unlock(readerWriterPath + "/reader");
        }

        public void unlockReader() {
            int readers;
            readerMutex.lock(readerWriterPath + "/reader");
            try {
                readers = zkClient.readData(ZkPath.LOCK_ROOT + readerWriterPath);
                if (readers == 1)
                    unlockWriter();
                readers--;
                zkClient.writeData(ZkPath.LOCK_ROOT + readerWriterPath, readers);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
            readerMutex.unlock(readerWriterPath + "/reader");
        }

        public void lockWriter() {
            // Acquire global lock
            String nodePath;
            String prevNodePath;
            List<String> nodes;
            String lockNode;

            try {
                nodePath = zkClient.createEphemeralSequential(ZkPath.READER_WRITER_LOCK_ROOT + "/writer/node", "");
            } catch (RuntimeException e) {
                e.printStackTrace();
                return;
            }
            lockNode = nodePath.split("/")[nodePath.split("/").length - 1];

            nodes = zkClient.getChildren(ZkPath.READER_WRITER_LOCK_ROOT + "/writer");
            nodes.sort(String::compareTo);
            if (lockNode.equals(nodes.get(0))) {
                zkClient.writeData(ZkPath.READER_WRITER_LOCK_ROOT + "/writer", nodePath);
                return;
            }

            prevNodePath = ZkPath.READER_WRITER_LOCK_ROOT + "/writer/" + nodes.get(nodes.indexOf(lockNode) - 1);

            while (true) {
                try {
                    if (!zkClient.exists(prevNodePath)) {
                        zkClient.writeData(ZkPath.READER_WRITER_LOCK_ROOT + "/writer", nodePath);
                        return;
                    }
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public void unlockWriter() {
            boolean r;
            String path;

            try {
                // the path to be deleted
                path = zkClient.readData(ZkPath.READER_WRITER_LOCK_ROOT + "/writer");
            } catch (RuntimeException e) {
                e.printStackTrace();
                return;
            }
            while (true) {
                try {
                    r = zkClient.delete(path);
                    if (r)
                        return;
                } catch (RuntimeException e) {
                    System.out.println(path);
                    if (zkClient.exists(path)) {
                        e.printStackTrace();
                    } else
                        return;
                }
            }
        }
    }

    public ReaderWriterLock readerWriter(String readerWriterPath) {
        return new ReaderWriterLock(readerWriterPath);
    }

}
