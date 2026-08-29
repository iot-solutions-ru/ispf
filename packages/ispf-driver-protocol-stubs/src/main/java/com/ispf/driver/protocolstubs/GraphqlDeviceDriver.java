package com.ispf.driver.protocolstubs;

/**
 * GraphQL protocol stub (graphql).
 * <p>
 * GraphQL HTTP stub.
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
