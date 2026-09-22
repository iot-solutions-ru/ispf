package com.ispf.driver.dlms;

import com.ispf.core.model.DataRecord;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverTransientException;
import com.ispf.driver.dlms.codec.DlmsTcpWrapperCodec;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * DLMS/COSEM client session over IEC 62056-47 TCP WRAPPER (no ACSE AARQ).
 */
final class DlmsClientCommunicator implements AutoCloseable {

    private final Socket socket;
    private final int clientAddress;
    private final int logicalDevice;

    DlmsClientCommunicator(
            String host,
            int port,
            int clientAddress,
            int logicalDevice,
            int timeoutMs
    ) throws DriverException {
        this.clientAddress = clientAddress;
        this.logicalDevice = logicalDevice;
        socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
        } catch (Exception ex) {
            closeQuietly();
            throw new DriverTransientException("DLMS connect failed", ex);
        }
    }

    boolean isOpen() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    Object readAttribute(DlmsPoint point) throws DriverException {
        try {
            byte[] request = DlmsTcpWrapperCodec.getRequest(point.objectType(), point.obis(), point.attributeIndex());
            byte[] response = exchange(request);
            return DlmsTcpWrapperCodec.parseGetResponse(response);
        } catch (Exception ex) {
            throw new DriverTransientException("DLMS read failed for " + point.obis(), ex);
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
            throw new DriverTransientException("DLMS write failed for " + point.obis(), ex);
        }
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
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }
}
