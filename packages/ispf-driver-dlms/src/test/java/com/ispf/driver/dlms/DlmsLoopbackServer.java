package com.ispf.driver.dlms;

import com.ispf.driver.dlms.codec.DlmsObjectType;
import com.ispf.driver.dlms.codec.DlmsTcpWrapperCodec;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal ISPF-owned DLMS WRAPPER meter for integration tests.
 */
final class DlmsLoopbackServer implements AutoCloseable {

    static final String ENERGY_OBIS = "1.0.1.8.0.255";
    static final String DEVICE_NAME_OBIS = "0.0.42.0.0.255";

    private final int clientSap;
    private final ServerSocket serverSocket;
    private final Thread serverThread;
    private final Map<String, Object> values = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    DlmsLoopbackServer(int clientSap) throws Exception {
        this.clientSap = clientSap;
        values.put(key(DlmsObjectType.DATA, DEVICE_NAME_OBIS, 2), "ISPF-TEST");
        values.put(key(DlmsObjectType.REGISTER, ENERGY_OBIS, 2), 42.0);
        serverSocket = new ServerSocket(0);
        serverThread = new Thread(this::serve, "dlms-loopback-server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    double energyValue() {
        return ((Number) values.get(key(DlmsObjectType.REGISTER, ENERGY_OBIS, 2))).doubleValue();
    }

    private void serve() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                Thread clientThread = new Thread(() -> handleClient(socket), "dlms-loopback-client");
                clientThread.setDaemon(true);
                clientThread.start();
            } catch (Exception ex) {
                if (running) {
                    throw new IllegalStateException("DLMS loopback accept failed", ex);
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        try (socket) {
            boolean associated = false;
            while (!socket.isClosed()) {
                DlmsTcpWrapperCodec.Frame frame = DlmsTcpWrapperCodec.readFrame(socket.getInputStream());
                byte[] request = frame.payload();
                int command = Byte.toUnsignedInt(request[0]);
                byte[] response;
                if (command == DlmsTcpWrapperCodec.CMD_ASSOCIATE_REQUEST) {
                    associated = frame.sourceWPort() == clientSap;
                    response = DlmsTcpWrapperCodec.associateResponse(associated);
                } else if (!associated) {
                    response = DlmsTcpWrapperCodec.associateResponse(false);
                } else if (command == DlmsTcpWrapperCodec.CMD_GET_REQUEST) {
                    DlmsTcpWrapperCodec.GetRequest get = DlmsTcpWrapperCodec.parseGetRequest(request);
                    Object value = values.get(key(get.objectType(), get.obis(), get.attributeIndex()));
                    response = DlmsTcpWrapperCodec.getResponse(value == null ? 1 : 0, value);
                } else if (command == DlmsTcpWrapperCodec.CMD_SET_REQUEST) {
                    DlmsTcpWrapperCodec.SetRequest set = DlmsTcpWrapperCodec.parseSetRequest(request);
                    values.put(key(set.objectType(), set.obis(), set.attributeIndex()), set.value());
                    response = DlmsTcpWrapperCodec.setResponse(0);
                } else {
                    response = DlmsTcpWrapperCodec.setResponse(1);
                }
                DlmsTcpWrapperCodec.writeFrame(socket.getOutputStream(), frame.destinationWPort(), frame.sourceWPort(), response);
            }
        } catch (Exception ignored) {
            // Client disconnects end the test session.
        }
    }

    private static String key(DlmsObjectType type, String obis, int attributeIndex) {
        return type.name() + ":" + obis + ":" + attributeIndex;
    }

    void closeServer() {
        close();
    }

    @Override
    public void close() {
        running = false;
        try {
            serverSocket.close();
        } catch (Exception ignored) {
            // best effort
        }
        try {
            serverThread.join(2000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
