package sjtu.sdic.kvstore;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import sjtu.sdic.kvstore.core.MasterInfo;
import sjtu.sdic.kvstore.core.GroupHandle;
import sjtu.sdic.kvstore.core.NodeResponse;
import sjtu.sdic.kvstore.core.RES_TYPE;
import sjtu.sdic.kvstore.rpc.RpcCall;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


enum REQ_TYPE {
    GET,
    PUT,
    DELETE,
    UNKNOWN
}

class Request{
    String key;
    String value;
    REQ_TYPE req;
}

public class Client {
    private MasterInfo master;

    private Client(String masterLocation) {
        String[] args = masterLocation.split(":");
        if (args.length < 2) {
            System.out.println("Master location should be the format <address:port>");
            System.exit(-1);
        }
        this.master = new MasterInfo();
        master.address = args[0];
        master.port = Integer.parseInt(args[1]);
    }

    private Request parseLine(String line) {
        Request req = new Request();
        String[] reqArgs = line.split(" ");

        if (reqArgs.length < 2) {
            if (reqArgs.length == 1 && reqArgs[0].equals("exit"))
                System.exit(0);
            req.req = REQ_TYPE.UNKNOWN;
            return req;
        }

        switch (reqArgs[0]) {
            case "get":
                req.req = REQ_TYPE.GET;
                req.key = reqArgs[1];
                break;
            case "put":
                if (reqArgs.length < 3) {
                    req.req = REQ_TYPE.UNKNOWN;
                    break;
                }
                req.req = REQ_TYPE.PUT;
                req.key = reqArgs[1];
                req.value = reqArgs[2];
                break;
            case "delete":
                req.req = REQ_TYPE.DELETE;
                req.key = reqArgs[1];
                break;
            default:
                req.req = REQ_TYPE.UNKNOWN;
                break;
        }
        return req;
    }

    private String doRequest(Request request) {
        GroupHandle handle;
        NodeResponse res;

        if (request.req == REQ_TYPE.UNKNOWN)
            return  "Unknown request";

        handle = RpcCall.getMasterRpcService(master).getNodes(request.key);

        switch (request.req) {
            case GET:
                try {
                    res = RpcCall.getNodeRpcService(handle.primaryNode).get(request.key, handle);
                    if (res.resType == RES_TYPE.SUCCESS && res.payload == null)
                        return  "NOT FOUND";
                    else return res.payload;
                } catch (RuntimeException e) {
                    return "Node Exception, Try again";
                }
            case PUT:
                try {
                    res = RpcCall.getNodeRpcService(handle.primaryNode).put(request.key, request.value, handle);
                    if (res.resType == RES_TYPE.SUCCESS)
                        return String.format("Put <%s, %s> ok", request.key, request.value);
                    return res.payload;
                } catch (RuntimeException e) {
                    return "Node Exception. Try again";
                }
            case DELETE:
                try {
                    res = RpcCall.getNodeRpcService(handle.primaryNode).delete(request.key, handle);
                    if(res.payload == null)
                        return "NOT FOUND";
                    if (res.resType == RES_TYPE.SUCCESS)
                        return String.format("Delete <%s, %s> ok", request.key, res.payload);
                    return res.payload;
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    return "Node Exception. Try again";
                }
            default:
                return "";
        }
    }


    private static void checkArgs(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage java -jar <jar> [address:port]");
            System.exit(-1);
        }
    }

    public static void main(String[] args) {
        Logger.getRootLogger().setLevel(Level.OFF);
        String line;
        BufferedReader buf;
        Request request;
        String output;

        checkArgs(args);
        Client c = new Client(args[0]);

        buf = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            try{
                System.out.print(" > ");
                line = buf.readLine();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            line = line.trim();
            if (line.equals(""))
                continue;
            request = c.parseLine(line);
            output = c.doRequest(request);
            System.out.println(output);
        }
    }
}
