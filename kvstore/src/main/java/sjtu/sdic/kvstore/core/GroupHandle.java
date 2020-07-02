package sjtu.sdic.kvstore.core;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * NodeHandle - Transfers from Master to Client, Client uses it to send request
 *              to Nodes
 */
@AllArgsConstructor
@NoArgsConstructor
public class GroupHandle {
    // version of Groups(Update whenever nodes state changed in a group)
    public int version;

    public NodeInfo primaryNode;

    public List<NodeInfo> nodes;
}
