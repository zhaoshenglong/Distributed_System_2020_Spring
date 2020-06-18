package sjtu.sdic.mapreduce.core;

import sjtu.sdic.mapreduce.common.Channel;
import sjtu.sdic.mapreduce.common.DoTaskArgs;
import sjtu.sdic.mapreduce.common.JobPhase;
import sjtu.sdic.mapreduce.common.Utils;
import sjtu.sdic.mapreduce.rpc.Call;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Cachhe on 2019/4/22.
 */
public class Scheduler {

    private static List<String> idleWorkers;
    /**
     * schedule() starts and waits for all tasks in the given phase (mapPhase
     * or reducePhase). the mapFiles argument holds the names of the files that
     * are the inputs to the map phase, one per map task. nReduce is the
     * number of reduce tasks. the registerChan argument yields a stream
     * of registered workers; each item is the worker's RPC address,
     * suitable for passing to {@link Call}. registerChan will yield all
     * existing registered workers (if any) and new ones as they register.
     *
     * @param jobName job name
     * @param mapFiles files' name (if in same dir, it's also the files' path)
     * @param nReduce the number of reduce task that will be run ("R" in the paper)
     * @param phase MAP or REDUCE
     * @param registerChan register info channel
     */

    public static void schedule(String jobName, String[] mapFiles, int nReduce, JobPhase phase, Channel<String> registerChan) {
        int nTasks = -1; // number of map or reduce tasks
        int nOther = -1; // number of inputs (for reduce) or outputs (for map)
        switch (phase) {
            case MAP_PHASE:
                nTasks = mapFiles.length;
                nOther = nReduce;
                break;
            case REDUCE_PHASE:
                nTasks = nReduce;
                nOther = mapFiles.length;
                break;
        }

        System.out.println(String.format("Schedule: %d %s tasks (%d I/Os)", nTasks, phase, nOther));

        /**
        // All ntasks tasks have to be scheduled on workers. Once all tasks
        // have completed successfully, schedule() should return.
        //
        // Your code here (Part III, Part IV).
        //
        */
        Lock lock = new ReentrantLock();
        Condition idleWorkerCond = lock.newCondition(); // signals when there are idle workers
        List<Thread> runThreads = new ArrayList<>();
        if (idleWorkers == null) {
            idleWorkers = new ArrayList<>();
        }
        List<Integer> tasks = new ArrayList<>(nTasks);
        for (int i = 0; i < nTasks; i++) {
            tasks.add(i);
        }

        Thread t = new Thread(() -> {
            while (true) {
                try {
                    String w = registerChan.read();
                    lock.lock();
                    idleWorkers.add(w);

                    idleWorkerCond.signal();        // notify task thread idle workers' existence
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    lock.unlock();
                }
            }
        });
        t.setDaemon(true);
        t.start();

        while (!tasks.isEmpty()) {
            int finalNOther = nOther;
            Thread run = new Thread(() -> {
                int task;
                String w;
                boolean taskCompleted = false;

                lock.lock();
                if (!tasks.isEmpty()) {
                    task = tasks.get(0);
                    tasks.remove(0);
                    while (!taskCompleted) {
                        if (!idleWorkers.isEmpty()) {
                            w = idleWorkers.get(0);
                            idleWorkers.remove(0);
                        } else {
                            try {
                                idleWorkerCond.await();
                                w = idleWorkers.get(0);
                                idleWorkers.remove(0);
                            } catch (InterruptedException e) {
                                w = "NO_SERVICE";
                                e.printStackTrace();
                            }
                        }
                        lock.unlock();
                        try {
                            Call.getWorkerRpcService(w).doTask(
                                    new DoTaskArgs(jobName, mapFiles[task > mapFiles.length ? 0 : task],
                                            phase, task, finalNOther));
                            taskCompleted = true;
                        } catch (RuntimeException e) {
                            e.printStackTrace();
                        }
                        lock.lock();
                        if (taskCompleted) {
                            idleWorkers.add(w);
                            idleWorkerCond.signal();
                        }
                    }
                }
                lock.unlock();
            });
            runThreads.add(run);
            run.start();
        }

        try {
            for (Thread thread: runThreads) {
                thread.join();
            }
            if(phase == JobPhase.REDUCE_PHASE)
                idleWorkers = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(String.format("Schedule: %s done", phase));
    }
}
