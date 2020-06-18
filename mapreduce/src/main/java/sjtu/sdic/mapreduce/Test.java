package sjtu.sdic.mapreduce;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONWriter;
import com.alibaba.fastjson.serializer.JSONSerializer;
import org.codehaus.jackson.map.JsonDeserializer;
import sjtu.sdic.mapreduce.common.KeyValue;

import java.io.*;
import java.nio.file.Path;
import java.security.Key;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Test {
    public static void main(String[] args) {
//        ArrayList<KeyValue> keyValues = new ArrayList<>();
//        keyValues.add(new KeyValue("1", "2"));
//        String str = JSONArray.toJSONString(keyValues);
//        System.out.println(str);
//
//        List<KeyValue> list = JSONArray.parseArray(str, KeyValue.class);
//        System.out.println(list);
//
//        String a = "abandon\tattend attach\r\navoid";
//        String[] words = a.split("[\n ]");
//        System.out.println(words.length);
//        for (String w : words) {
//            System.out.println(w);
//        }
//
//        System.out.println("10".compareTo("2"));
//
//        List<Boolean> booleans = new ArrayList<>(10);
//        for (int i = 0; i < 10; i++) {
//            booleans.add(Boolean.FALSE);
//        }
//        booleans.forEach(System.out::println);
//
//        String s = "123456";
//        System.out.println(s.substring(0, 3));

//        Pattern p = Pattern.compile("\\d+");
//        Matcher m = p.matcher("absolute123dsa45");
//
////        System.out.println(m.groupCount());
////        System.out.println(m.start(1));
////        System.out.println(m.end(1));
//        System.out.println(m.group());

        Pattern p=Pattern.compile("\\d+");
        Matcher m=p.matcher("我的QQ是:456456 我的电话是:0532214 我的邮箱是:aaa123@aaa.com");
        while(m.find()) {
            System.out.println(m.group());
        }

        Set<String> set = new HashSet<>(Arrays.asList("1", "2", "3", "10", "11", "20"));
        List<String> list = new ArrayList<>(set);
        Collections.sort(list);
        System.out.println(String.join(",", list));
    }

}
