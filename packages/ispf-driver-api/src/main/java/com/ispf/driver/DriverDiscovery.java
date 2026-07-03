package com.ispf.driver;

import java.util.List;

/**
 * Optional driver capability for endpoint discovery (BL-80).
 */
public interface DriverDiscovery {

    record Node(String nodeId, String displayName, String nodeClass) {
    }

    List<Node> browseChildren(String parentNodeId) throws DriverException;
}
