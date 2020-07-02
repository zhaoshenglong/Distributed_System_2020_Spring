package sjtu.sdic.kvstore.core;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import sjtu.sdic.kvstore.common.LogOps;
import sjtu.sdic.kvstore.common.Utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Very simple logger
 */

public class NodeLogger {
    private final String logFileName;
    private final String checkpointFileName;

    public NodeLogger(String logFileName, String checkpointFileName) {
        this.logFileName = logFileName;
        this.checkpointFileName = checkpointFileName;
    }

    public void append(LogOps op, String key, String val) {
        File file = new File(logFileName);

        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try(
                BufferedWriter out =
                        new BufferedWriter(
                                new OutputStreamWriter(
                                        new FileOutputStream(file, true)))
        ) {
            if (op == LogOps.PUT) {
                out.append(String.format("%s %s %s\n", op.name(), key, val));
            } else if (op == LogOps.DELETE) {
                out.append(String.format("%s %s\n", op.name(), key));
            } else if (op == LogOps.CHECKPOINT) {
                // 序列化交给调用者执行，先序列化再log
                out.append(String.format("%s\n", op.name()));
            }
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public SortedMap<Integer, ConcurrentHashMap<String, String>> restore() {
        SortedMap<Integer, ConcurrentHashMap<String, String>> map = new ConcurrentSkipListMap<>();
        String line;

        try(
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(new FileInputStream(new File(logFileName))))
        ){

            while((line = in.readLine()) != null) {
                String[] args = line.split(" ");
                switch (args[0].trim()) {
                    case "PUT":
                        map.computeIfAbsent(Utils.hash(args[1]), k -> new ConcurrentHashMap<>());
                        map.get(Utils.hash(args[1])).put(args[1], args[2]);
                        break;
                    case "DELETE":
                        if (map.get(Utils.hash(args[1])) != null)
                            map.get(Utils.hash(args[1])).remove(args[1]);
                        break;
                    case "CHECKPOINT":
                        map = readCheckpoint();
                        break;
                    default:
                        break;
                }
            }
        } catch (IOException e) {
//            e.printStackTrace();
        }
        return map;
    }

    public void writeCheckpoint(SortedMap<Integer, ConcurrentHashMap<String, String>> data) {
        File file = new File(checkpointFileName);

        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try(
                BufferedWriter out =
                        new BufferedWriter(
                                new OutputStreamWriter(
                                        new FileOutputStream(file)))
        ) {
            out.write(JSON.toJSONString(data));
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public SortedMap<Integer, ConcurrentHashMap<String, String>> readCheckpoint() {

        File file = new File(checkpointFileName);
        try {
             return JSON.parseObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8),
                     new TypeReference<SortedMap<Integer, ConcurrentHashMap<String, String>>> (){});
        } catch (IOException e) {
//            e.printStackTrace();
            return new ConcurrentSkipListMap<>();
        }
    }
}
