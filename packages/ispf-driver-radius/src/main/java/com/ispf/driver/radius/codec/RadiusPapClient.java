package com.ispf.driver.radius.codec;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Minimal RADIUS PAP client for Access-Request authentication probes.
 */
public final class RadiusPapClient {

    public static final int ACCESS_REQUEST = 1;
    public static final int ACCESS_ACCEPT = 2;
    public static final int ACCESS_REJECT = 3;

    private static final int HEADER_LENGTH = 20;
    private static final int ATTR_USER_NAME = 1;
    private static final int ATTR_USER_PASSWORD = 2;
    private static final int MAX_PACKET_LENGTH = 4096;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String host;
    private final int port;
    private final byte[] secret;
    private final int timeoutMs;

    public RadiusPapClient(String host, int port, String secret, int timeoutMs) {
        this.host = host;
        this.port = port;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.timeoutMs = timeoutMs;
    }

    public Response authenticate(String username, String password) throws IOException {
        byte identifier = (byte) RANDOM.nextInt(256);
        byte[] requestAuthenticator = new byte[16];
        RANDOM.nextBytes(requestAuthenticator);

        byte[] userName = attribute(ATTR_USER_NAME, username.getBytes(StandardCharsets.UTF_8));
        byte[] userPassword = attribute(ATTR_USER_PASSWORD, encryptPapPassword(password, requestAuthenticator));
        int length = HEADER_LENGTH + userName.length + userPassword.length;

        ByteBuffer request = ByteBuffer.allocate(length);
        request.put((byte) ACCESS_REQUEST);
        request.put(identifier);
        request.putShort((short) length);
        request.put(requestAuthenticator);
        request.put(userName);
        request.put(userPassword);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            byte[] payload = request.array();
            DatagramPacket outgoing = new DatagramPacket(
                    payload,
                    payload.length,
                    InetAddress.getByName(host),
                    port
            );
            socket.send(outgoing);

            byte[] responseBuffer = new byte[MAX_PACKET_LENGTH];
            DatagramPacket incoming = new DatagramPacket(responseBuffer, responseBuffer.length);
            socket.receive(incoming);

            return parseResponse(
                    Arrays.copyOf(incoming.getData(), incoming.getLength()),
                    identifier,
                    requestAuthenticator
            );
        }
    }

    private Response parseResponse(byte[] packet, byte expectedIdentifier, byte[] requestAuthenticator)
            throws IOException {
        if (packet.length < HEADER_LENGTH) {
            throw new IOException("RADIUS response is shorter than header");
        }
        int code = packet[0] & 0xFF;
        byte identifier = packet[1];
        int length = ((packet[2] & 0xFF) << 8) | (packet[3] & 0xFF);
        if (identifier != expectedIdentifier) {
            throw new IOException("RADIUS response identifier mismatch");
        }
        if (length < HEADER_LENGTH || length > packet.length) {
            throw new IOException("Invalid RADIUS response length");
        }
        byte[] response = Arrays.copyOf(packet, length);
        if (!validResponseAuthenticator(response, requestAuthenticator)) {
            throw new IOException("Invalid RADIUS response authenticator");
        }
        return new Response(code);
    }

    private boolean validResponseAuthenticator(byte[] response, byte[] requestAuthenticator) throws IOException {
        byte[] expected = responseAuthenticator(response, requestAuthenticator);
        byte[] actual = Arrays.copyOfRange(response, 4, HEADER_LENGTH);
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] responseAuthenticator(byte[] response, byte[] requestAuthenticator) throws IOException {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(response[0]);
            md5.update(response[1]);
            md5.update(response[2]);
            md5.update(response[3]);
            md5.update(requestAuthenticator);
            if (response.length > HEADER_LENGTH) {
                md5.update(response, HEADER_LENGTH, response.length - HEADER_LENGTH);
            }
            md5.update(secret);
            return md5.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 digest is unavailable", e);
        }
    }

    private byte[] encryptPapPassword(String password, byte[] requestAuthenticator) throws IOException {
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        int paddedLength = Math.max(16, ((passwordBytes.length + 15) / 16) * 16);
        byte[] padded = Arrays.copyOf(passwordBytes, paddedLength);
        byte[] encrypted = new byte[padded.length];
        byte[] previous = requestAuthenticator;

        for (int offset = 0; offset < padded.length; offset += 16) {
            byte[] blockDigest = passwordBlockDigest(previous);
            for (int i = 0; i < 16; i++) {
                encrypted[offset + i] = (byte) (padded[offset + i] ^ blockDigest[i]);
            }
            previous = Arrays.copyOfRange(encrypted, offset, offset + 16);
        }
        return encrypted;
    }

    private byte[] passwordBlockDigest(byte[] previous) throws IOException {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(secret);
            md5.update(previous);
            return md5.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 digest is unavailable", e);
        }
    }

    private static byte[] attribute(int type, byte[] value) throws IOException {
        if (value.length > 253) {
            throw new IOException("RADIUS attribute is too long: " + type);
        }
        byte[] attribute = new byte[value.length + 2];
        attribute[0] = (byte) type;
        attribute[1] = (byte) attribute.length;
        System.arraycopy(value, 0, attribute, 2, value.length);
        return attribute;
    }

    public record Response(int code) {
    }
}
