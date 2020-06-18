package sjtu.sdic.mapreduce.core;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import sjtu.sdic.mapreduce.common.KeyValue;
import sjtu.sdic.mapreduce.common.Utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.Key;
import java.util.*;

/**
 * Created by Cachhe on 2019/4/19.
 */
public class Reducer {

    /**
     * 
     * 	doReduce manages one reduce task: it should read the intermediate
     * 	files for the task, sort the intermediate key/value pairs by key,
     * 	call the user-defined reduce function {@code reduceF} for each key,
     * 	and write reduceF's output to disk.
     * 	
     * 	You'll need to read one intermediate file from each map task;
     * 	{@code reduceName(jobName, m, reduceTask)} yields the file
     * 	name from map task m.
     *
     * 	Your {@code doMap()} encoded the key/value pairs in the intermediate
     * 	files, so you will need to decode them. If you used JSON, you can refer
     * 	to related docs to know how to decode.
     * 	
     *  In the original paper, sorting is optional but helpful. Here you are
     *  also required to do sorting. Lib is allowed.
     * 	
     * 	{@code reduceF()} is the application's reduce function. You should
     * 	call it once per distinct key, with a slice of all the values
     * 	for that key. {@code reduceF()} returns the reduced value for that
     * 	key.
     * 	
     * 	You should write the reduce output as JSON encoded KeyValue
     * 	objects to the file named outFile. We require you to use JSON
     * 	because that is what the merger than combines the output
     * 	from all the reduce tasks expects. There is nothing special about
     * 	JSON -- it is just the marshalling format we chose to use.
     * 	
     * 	Your code here (Part I).
     * 	
     * 	
     * @param jobName the name of the whole MapReduce job
     * @param reduceTask which reduce task this is
     * @param outFile write the output here
     * @param nMap the number of map tasks that were run ("M" in the paper)
     * @param reduceF user-defined reduce function
     */
    public static void doReduce(String jobName, int reduceTask, String outFile, int nMap, ReduceFunc reduceF) {
        String content;
        List<KeyValue> reducerList = new ArrayList<>();
        Map<String, ArrayList<String>> reducerMap = new HashMap<>();
        Map<String, String> result = new HashMap<>();


        for (int i = 0; i < nMap; i++) {
            content = readFileString(Utils.reduceName(jobName, i, reduceTask));
            List<KeyValue> intermediateList = JSONArray.parseArray(content, KeyValue.class);
            reducerList.addAll(intermediateList);
        }
        // forceful sorting method using here
        reducerList.sort(Comparator.comparing(o -> o.key));

        reducerList.forEach(item -> {
            reducerMap.computeIfAbsent(item.key, k -> new ArrayList<String>());
            reducerMap.get(item.key).add(item.value);
        });

        reducerMap.forEach((key, list) -> {
            result.put(key,
                    reduceF.reduce(key, reducerMap.get(key).toArray(new String[0])));});

        dumpToFile(outFile, JSONArray.toJSONString(result));
    }

    /**
     * @param filename the name of input file
     * @return content of file {@code filename}
     */
    public static String readFileString(String filename) {
        StringBuilder content = new StringBuilder();
        String line;

        try(
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(new FileInputStream(new File(filename))))
        ){
            while((line = in.readLine()) != null) {
                content.append(line);
                content.append("\n");
            }
            return content.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * dump intermediate output into file {@code filename}
     * @param filename the name of output file
     * @param content content to be dumped, in JSON format
     */
    public static void dumpToFile(String filename, String content) {
        File file = new File(filename);

        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try(
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)))
        ) {
            out.write(content);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
