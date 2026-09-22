package com.ispf.driver.ipmi;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

/**
 * Minimal RMCP ping (Get Channel Authentication Capabilities) for IPMI reachability.
 */
final class RmcpPingClient {

    private static final byte RMCP_VERSION = 0x06;

    private RmcpPingClient() {
    }

    static boolean ping(String host, int port, int timeoutMs) {
        byte[] request = buildAuthCapabilitiesRequest();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            InetAddress address = InetAddress.getByName(host);
            DatagramPacket packet = new DatagramPacket(request, request.length, address, port);
            socket.send(packet);
            byte[] buffer = new byte[256];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);
            return response.getLength() >= 4 && buffer[0] == RMCP_VERSION;
        } catch (SocketTimeoutException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Published pre-session Get Channel Authentication Capabilities RMCP/IPMI packet.
     * Octets are fixed to the wire layout (not derived from a session encoder).
     */
    static byte[] buildAuthCapabilitiesRequest() {
        // RMCP 06 00 FF 07 | auth none + session 0 | len 9 | rs 20 netFn 18 cs C8 | rq 81 seq 0 cmd 38 | 8E 04 | cs B5
        return new byte[] {
                0x06, 0x00, (byte) 0xFF, 0x07,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x09, 0x20, 0x18,
                (byte) 0xC8, (byte) 0x81, 0x00, 0x38,
                (byte) 0x8E, 0x04, (byte) 0xB5
        };
    }
}
