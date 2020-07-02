package sjtu.sdic.kvstore;

import java.util.SortedMap;
import java.util.TreeMap;

public class Test {
    public static void main(String[] args) {
        SortedMap<Integer, String > m = new TreeMap<>(),
            subMap, r;
        for(int i = 0; i < 99; i++) {
            m.put(i, String.valueOf(i));
        }
        subMap = m.subMap(4, 50);
        r = new TreeMap<>(subMap);
        System.out.println(r.entrySet().size());
        subMap.clear();
        System.out.println(r.entrySet().size());
        System.out.println(m.entrySet().size());
        m.keySet().forEach(System.out::println);
    }
}
