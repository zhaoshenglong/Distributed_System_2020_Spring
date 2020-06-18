package sjtu.sdic.mapreduce;

import org.apache.commons.io.filefilter.WildcardFileFilter;
import sjtu.sdic.mapreduce.common.KeyValue;
import sjtu.sdic.mapreduce.core.Master;
import sjtu.sdic.mapreduce.core.Worker;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Cachhe on 2019/4/24.
 */
public class InvertedIndex {
    private final static String wordPatternStr = "[a-zA-Z0-9]+";

    public static List<KeyValue> mapFunc(String file, String value) {
        // Your code here (Part V)
        Pattern wordPattern = Pattern.compile(wordPatternStr);
        ArrayList<KeyValue> result = new ArrayList<>();
        Matcher matcher;

        matcher = wordPattern.matcher(value);
        while (matcher.find()) {
            result.add(new KeyValue(matcher.group(), file));
        }
        return result;
    }

    public static String reduceFunc(String key, String[] values) {
        //  Your code here (Part V)
        int nDocs = 0;
        Set<String> valueSet = new HashSet<>(Arrays.asList(values));
        List<String> valueList = new ArrayList<>(valueSet);
        StringBuilder result = new StringBuilder();

        nDocs = valueSet.size();
        Collections.sort(valueList);

        result.append(nDocs);
        result.append(" ");
        result.append(String.join(",", valueList));
        return result.toString();
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("error: see usage comments in file");
        } else if (args[0].equals("master")) {
            Master mr;

            String src = args[2];
            File file = new File(".");
            String[] files = file.list(new WildcardFileFilter(src));
            if (args[1].equals("sequential")) {
                mr = Master.sequential("iiseq", files, 3, InvertedIndex::mapFunc, InvertedIndex::reduceFunc);
            } else {
                mr = Master.distributed("wcdis", files, 3, args[1]);
            }
            mr.mWait();
        } else {
            Worker.runWorker(args[1], args[2], InvertedIndex::mapFunc, InvertedIndex::reduceFunc, 100, null);
        }
    }
}
