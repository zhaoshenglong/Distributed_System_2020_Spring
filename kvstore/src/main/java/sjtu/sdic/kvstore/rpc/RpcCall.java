package sjtu.sdic.kvstore.rpc;

import com.alipay.sofa.rpc.config.ConsumerConfig;
import sjtu.sdic.kvstore.common.Utils;
import sjtu.sdic.kvstore.core.MasterInfo;
import sjtu.sdic.kvstore.core.NodeInfo;


public class RpcCall {
    public static MasterRpcService getMasterRpcService(MasterInfo m) {
        ConsumerConfig<MasterRpcService> consumerConfig = new ConsumerConfig<MasterRpcService>()
                .setInterfaceId(MasterRpcService.class.getName()) // Specify the interface
                .setUniqueId(Utils.getMasterId(m))
                .setProtocol("bolt") // Specify the protocol
                .setDirectUrl("bolt://" + m.address + ":" + m.port)
                .setRepeatedReferLimit(-1);
        return consumerConfig.refer();
    }

    public static NodeRpcService getNodeRpcService(NodeInfo n) {
        ConsumerConfig<NodeRpcService> consumerConfig = new ConsumerConfig<NodeRpcService>()
                .setInterfaceId(NodeRpcService.class.getName()) // Specify the interface
                .setUniqueId(Utils.getNodeId(n))
                .setProtocol("bolt") // Specify the protocol
                .setDirectUrl("bolt://" + n.address + ":" + n.port)
                .setRepeatedReferLimit(-1)
                ;
        return consumerConfig.refer();
    }
}
