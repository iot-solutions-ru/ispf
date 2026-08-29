package com.ispf.driver.graphql;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * GraphQL protocol stub (graphql).
 * <p>
 * GraphQL HTTP stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class GraphqlDeviceDriver extends ProtocolStubDeviceDriver {

    public GraphqlDeviceDriver() {
        super(
                "graphql",
                "GraphQL Driver",
                "GraphQL HTTP stub",
                80
        );
    }
}
