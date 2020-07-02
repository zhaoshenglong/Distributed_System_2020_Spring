package sjtu.sdic.kvstore.common;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.omg.CORBA.PUBLIC_MEMBER;
import org.yaml.snakeyaml.Yaml;
import sjtu.sdic.kvstore.core.Master;
import sjtu.sdic.kvstore.core.MasterInfo;
import sjtu.sdic.kvstore.core.Node;
import sjtu.sdic.kvstore.core.NodeInfo;

import java.io.*;
import java.util.Random;

@Slf4j
public class Utils {
    private final static String characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final static int MAX_RAND_INT = 1024;

    public static int hash(String key) {
        return Hashing
                .murmur3_32()
                .newHasher()
                .putString(key, Charsets.UTF_8)
                .hash()
                .asInt() & Integer.MAX_VALUE;
    }

    public static <T> T loadConfig(String filename, Class<T> type) {
        try(
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(new FileInputStream(new File(filename))))
        ) {
            return new Yaml().loadAs(in, type);
        } catch (IOException e) {
            log.error("Load config {} failed, nested cause is {}", filename, e.getCause());
            return null;
        }
    }

    public static <T> T stringToYaml(String str, Class<T> type) {
        return new Yaml().loadAs(str, type);
    }

    public static String yamlToString(Object o) {
        return new Yaml().dump(o);
    }

    public static String randomKeyGen(int length) {
        int i;
        Random random = new Random();
        StringBuilder s = new StringBuilder();

        for(i=0; i < length; i++){
            int number=random.nextInt(62);
            s.append(characters.charAt(number));
        }
        return s.toString();
    }

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

    public static int randomInt() {
        return new Random().nextInt(MAX_RAND_INT);
    }

    public static String genVirtualGroupName(String groupName, int i) {
        return "virt-" + Utils.hash(groupName) % 32 + "-" + groupName + "#" + i;
    }

    public static String getNodeId(NodeInfo n) {
        return n.address + ":" + n.port;
    }

    public static String getMasterId(MasterInfo m) {
        return m.address + ":" + m.port;
    }
}
