package com.ispf.driver.dlms;

import com.ispf.core.model.DataRecord;
import com.ispf.driver.DriverException;
import gurux.common.ReceiveParameters;
import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXDLMSException;
import gurux.dlms.GXReplyData;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.DataType;
import gurux.dlms.enums.ErrorCode;
import gurux.dlms.enums.InterfaceType;
import gurux.net.GXNet;

import java.util.concurrent.TimeUnit;

/**
 * Gurux DLMS/COSEM client session over TCP WRAPPER.
 */
final class DlmsClientCommunicator implements AutoCloseable {

    private final GXNet media;
    private final GXDLMSClient client;
    private final int timeoutMs;
    private boolean associated;

    DlmsClientCommunicator(
            String host,
            int port,
            int clientAddress,
            int logicalDevice,
            int timeoutMs
    ) throws DriverException {
        this.timeoutMs = timeoutMs;
        this.client = new GXDLMSClient(true);
        client.setUseLogicalNameReferencing(true);
        client.setInterfaceType(InterfaceType.WRAPPER);
        client.setAuthentication(Authentication.NONE);
        client.setClientAddress(clientAddress);
        client.setServerAddress(GXDLMSClient.getServerAddress(logicalDevice, 1));
        try {
            media = new GXNet(gurux.net.enums.NetworkType.TCP, host, port);
            media.open();
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
        return media != null && media.isOpen() && associated;
    }

    Object readAttribute(DlmsPoint point) throws DriverException {
        try {
            byte[][] data = client.read(point.obis(), point.objectType(), point.attributeIndex());
            GXReplyData reply = new GXReplyData();
            readDataBlock(data, reply);
            return reply.getValue();
        } catch (GXDLMSException ex) {
            throw new DriverException("DLMS read failed for " + point.obis(), ex);
        } catch (Exception ex) {
            throw new DriverException("DLMS read failed for " + point.obis(), ex);
        }
    }

    void writeAttribute(DlmsPoint point, DataRecord value) throws DriverException {
        try {
            Object raw = DlmsValueCodec.extractWriteValue(value, point);
            DataType dataType = DlmsValueCodec.writeDataType(point, raw);
            byte[][] data = client.write(
                    point.obis(),
                    raw,
                    dataType,
                    point.objectType(),
                    point.attributeIndex()
            );
            GXReplyData reply = new GXReplyData();
            readDataBlock(data, reply);
            if (reply.getError() != 0 && reply.getError() != ErrorCode.OK.getValue()) {
                throw new DriverException("DLMS write rejected for " + point.obis() + " (error=" + reply.getError() + ")");
            }
        } catch (DriverException ex) {
            throw ex;
        } catch (GXDLMSException ex) {
            throw new DriverException("DLMS write failed for " + point.obis(), ex);
        } catch (Exception ex) {
            throw new DriverException("DLMS write failed for " + point.obis(), ex);
        }
    }

    private void associate() throws Exception {
        GXReplyData reply = new GXReplyData();
        byte[] snrm = client.snrmRequest();
        if (snrm.length != 0) {
            readDlmsPacket(snrm, reply);
            client.parseUAResponse(reply.getData());
        }
        reply.clear();
        readDataBlock(client.aarqRequest(), reply);
        client.parseAareResponse(reply.getData());
        associated = true;
    }

    private void readDataBlock(byte[][] data, GXReplyData reply) throws Exception {
        if (data == null) {
            return;
        }
        for (byte[] packet : data) {
            reply.clear();
            readDataBlock(packet, reply);
        }
    }

    private void readDataBlock(byte[] data, GXReplyData reply) throws Exception {
        if (data == null || data.length == 0) {
            return;
        }
        readDlmsPacket(data, reply);
        while (reply.isMoreData()) {
            readDlmsPacket(client.receiverReady(reply), reply);
        }
    }

    private void readDlmsPacket(byte[] data, GXReplyData reply) throws Exception {
        if (data == null || data.length == 0) {
            return;
        }
        Object endOfPacket = null;
        GXByteBuffer buffer = new GXByteBuffer();
        ReceiveParameters<byte[]> params = new ReceiveParameters<>(byte[].class);
        params.setEop(endOfPacket);
        params.setAllData(true);
        params.setCount(client.getFrameSize(buffer));
        params.setWaitTime(timeoutMs);

        synchronized (media.getSynchronous()) {
            media.send(data, null);
            if (!media.receive(params)) {
                throw new DriverException("DLMS receive timeout");
            }
            buffer = new GXByteBuffer(params.getReply());
            GXReplyData notify = new GXReplyData();
            while (!client.getData(buffer, reply, notify)) {
                params.setReply(null);
                params.setCount(client.getFrameSize(buffer));
                if (!media.receive(params)) {
                    throw new DriverException("DLMS receive timeout");
                }
                buffer.set(params.getReply());
            }
        }
        if (reply.getError() != 0 && reply.getError() != ErrorCode.OK.getValue()) {
            if (reply.getError() == ErrorCode.REJECTED.getValue()) {
                TimeUnit.MILLISECONDS.sleep(200);
                readDlmsPacket(data, reply);
                return;
            }
            throw new GXDLMSException(reply.getError());
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
        if (media != null && media.isOpen()) {
            try {
                GXReplyData reply = new GXReplyData();
                readDlmsPacket(client.disconnectRequest(), reply);
            } catch (Exception ignored) {
                // best effort
            }
            try {
                media.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }
}
