package com.ispf.driver.opcuaserver;

/**
 * Documented interop contract for the embedded OPC UA server driver (BL-143).
 * <p>
 * External OPC UA clients and the {@code opcua} client driver connect to these endpoints.
 */
public final class OpcUaServerInterop {

    public static final String APPLICATION_URI = "urn:ispf:driver:opcua-server";
    public static final String NAMESPACE_URI = "urn:ispf:opcua:server";
    public static final String ENDPOINT_PATH = "/ispf";
    public static final String VARIABLES_FOLDER = "IspfVariables";
    public static final int DEFAULT_BIND_PORT = 4840;
    public static final int DEFAULT_NAMESPACE_INDEX = 2;

    private OpcUaServerInterop() {
    }

    /**
     * Standard endpoint URL advertised to OPC UA clients.
     *
     * @param bindPort TCP port (default {@value #DEFAULT_BIND_PORT})
     */
    public static String endpointUrl(int bindPort) {
        return "opc.tcp://localhost:" + bindPort + ENDPOINT_PATH;
    }

    /**
     * Browse path from Objects root to mapped variable nodes.
     */
    public static String browsePath(String variableName) {
        return "Objects/" + VARIABLES_FOLDER + "/" + variableName;
    }
}
