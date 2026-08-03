package com.ispf.driver.dlms;

import com.ispf.core.model.DataRecord;
import com.ispf.driver.DriverException;
import com.ispf.driver.dlms.codec.DlmsTcpWrapperCodec;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Clean-room DLMS/COSEM client session over TCP WRAPPER.
 */
final class DlmsClientCommunicator implements AutoCloseable {

    private final Socket socket;
    private final int clientAddress;
    private final int logicalDevice;
    private final int timeoutMs;
    private boolean associated;

    DlmsClientCommunicator(
            String host,
            int port,
            int clientAddress,
            int logicalDevice,
            int timeoutMs
    ) throws DriverException {
        this.clientAddress = clientAddress;
        this.logicalDevice = logicalDevice;
        this.timeoutMs = timeoutMs;
        socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            associate();
        } catch (DriverException ex) {
            closeQuietly();
            throw ex;
        } catch (Exception ex) {
            closeQuietly();
            throw new DriverException("DLMS connect failed", ex);
        }
    }

    boolean isOpen() {
        return socket != null && socket.isConnected() && !socket.isClosed() && associated;
    }

    Object readAttribute(DlmsPoint point) throws DriverException {
        try {
            byte[] request = DlmsTcpWrapperCodec.getRequest(point.objectType(), point.obis(), point.attributeIndex());
            byte[] response = exchange(request);
            return DlmsTcpWrapperCodec.parseGetResponse(response);
        } catch (Exception ex) {
            throw new DriverException("DLMS read failed for " + point.obis(), ex);
        }
    }

    void writeAttribute(DlmsPoint point, DataRecord value) throws DriverException {
        try {
            Object raw = DlmsValueCodec.extractWriteValue(value, point);
            byte[] request = DlmsTcpWrapperCodec.setRequest(point.objectType(), point.obis(), point.attributeIndex(), raw);
            DlmsTcpWrapperCodec.parseSetResponse(exchange(request));
        } catch (DriverException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DriverException("DLMS write failed for " + point.obis(), ex);
        }
    }

    private void associate() throws Exception {
        byte[] response = exchange(DlmsTcpWrapperCodec.associateRequest(clientAddress, logicalDevice));
        if (!DlmsTcpWrapperCodec.parseAssociateResponse(response)) {
            throw new DriverException("DLMS association rejected");
        }
        associated = true;
    }

    private byte[] exchange(byte[] payload) throws Exception {
        synchronized (socket) {
            DlmsTcpWrapperCodec.writeFrame(socket.getOutputStream(), clientAddress, logicalDevice, payload);
            DlmsTcpWrapperCodec.Frame response = DlmsTcpWrapperCodec.readFrame(socket.getInputStream());
            return response.payload();
        }
    }

    private void closeQuietly() {
        try {
            close();
        } catch (Exception ignored) {
            // best effort
        }
    }

    @Override
    public void close() {
        associated = false;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }
}
