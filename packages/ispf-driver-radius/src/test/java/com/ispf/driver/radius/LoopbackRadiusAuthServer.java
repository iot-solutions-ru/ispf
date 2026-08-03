package com.ispf.driver.radius;

import com.ispf.driver.radius.codec.RadiusPapClient;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class LoopbackRadiusAuthServer implements AutoCloseable {

    private static final int HEADER_LENGTH = 20;
    private static final int ATTR_USER_NAME = 1;
    private static final int ATTR_USER_PASSWORD = 2;
    private static final int MAX_PACKET_LENGTH = 4096;
    private static final Map<String, String> USERS = Map.of("alice", "wonderland");

    private final String secret;
    private final byte[] secretBytes;
    private final DatagramSocket socket;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "loopback-radius-auth");
        thread.setDaemon(true);
        return thread;
    });
    private final CountDownLatch started = new CountDownLatch(1);

    LoopbackRadiusAuthServer(String secret) throws IOException {
        this.secret = secret;
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.socket = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
    }

    int port() {
        return socket.getLocalPort();
    }

    void start() {
        executor.submit(this::serve);
    }

    boolean awaitStarted(long timeout, TimeUnit unit) throws InterruptedException {
        return started.await(timeout, unit);
    }

    void stop() {
        try {
            close();
        } catch (Exception ignored) {
            // best effort test cleanup
        }
    }

    private void serve() {
        started.countDown();
        byte[] buffer = new byte[MAX_PACKET_LENGTH];
        while (!socket.isClosed()) {
            DatagramPacket request = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(request);
                handle(request);
            } catch (IOException e) {
                if (socket.isClosed()) {
                    return;
                }
            }
        }
    }

    private void handle(DatagramPacket request) throws IOException {
        byte[] packet = Arrays.copyOf(request.getData(), request.getLength());
        if (packet.length < HEADER_LENGTH || (packet[0] & 0xFF) != RadiusPapClient.ACCESS_REQUEST) {
            return;
        }
        int length = ((packet[2] & 0xFF) << 8) | (packet[3] & 0xFF);
        if (length < HEADER_LENGTH || length > packet.length) {
            return;
        }
        byte[] requestAuthenticator = Arrays.copyOfRange(packet, 4, HEADER_LENGTH);
        Attributes attributes = readAttributes(Arrays.copyOf(packet, length), requestAuthenticator);
        int responseCode = USERS.getOrDefault(attributes.username(), "").equals(attributes.password())
                ? RadiusPapClient.ACCESS_ACCEPT
                : RadiusPapClient.ACCESS_REJECT;

        byte[] response = response(responseCode, packet[1], requestAuthenticator);
        DatagramPacket outgoing = new DatagramPacket(
                response,
                response.length,
                request.getAddress(),
                request.getPort()
        );
        socket.send(outgoing);
    }

    private Attributes readAttributes(byte[] packet, byte[] requestAuthenticator) throws IOException {
        String username = "";
        String password = "";
        int offset = HEADER_LENGTH;
        while (offset < packet.length) {
            if (offset + 2 > packet.length) {
                break;
            }
            int type = packet[offset] & 0xFF;
            int length = packet[offset + 1] & 0xFF;
            if (length < 2 || offset + length > packet.length) {
                break;
            }
            byte[] value = Arrays.copyOfRange(packet, offset + 2, offset + length);
            if (type == ATTR_USER_NAME) {
                username = new String(value, StandardCharsets.UTF_8);
            } else if (type == ATTR_USER_PASSWORD) {
                password = decryptPapPassword(value, requestAuthenticator);
            }
            offset += length;
        }
        return new Attributes(username, password);
    }

    private String decryptPapPassword(byte[] encrypted, byte[] requestAuthenticator) throws IOException {
        if (encrypted.length == 0 || encrypted.length % 16 != 0) {
            throw new IOException("Invalid RADIUS PAP password length");
        }
        byte[] password = new byte[encrypted.length];
        byte[] previous = requestAuthenticator;
        for (int offset = 0; offset < encrypted.length; offset += 16) {
            byte[] digest = passwordBlockDigest(previous);
            for (int i = 0; i < 16; i++) {
                password[offset + i] = (byte) (encrypted[offset + i] ^ digest[i]);
            }
            previous = Arrays.copyOfRange(encrypted, offset, offset + 16);
        }
        int length = password.length;
        while (length > 0 && password[length - 1] == 0) {
            length--;
        }
        return new String(password, 0, length, StandardCharsets.UTF_8);
    }

    private byte[] passwordBlockDigest(byte[] previous) throws IOException {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(secretBytes);
            md5.update(previous);
            return md5.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 digest is unavailable", e);
        }
    }

    private byte[] response(int code, byte identifier, byte[] requestAuthenticator) throws IOException {
        byte[] response = new byte[HEADER_LENGTH];
        response[0] = (byte) code;
        response[1] = identifier;
        response[2] = 0;
        response[3] = HEADER_LENGTH;
        byte[] authenticator = responseAuthenticator(response, requestAuthenticator);
        System.arraycopy(authenticator, 0, response, 4, authenticator.length);
        return response;
    }

    private byte[] responseAuthenticator(byte[] response, byte[] requestAuthenticator) throws IOException {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(response[0]);
            md5.update(response[1]);
            md5.update(response[2]);
            md5.update(response[3]);
            md5.update(requestAuthenticator);
            md5.update(secret.getBytes(StandardCharsets.UTF_8));
            return md5.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 digest is unavailable", e);
        }
    }

    @Override
    public void close() throws Exception {
        socket.close();
        executor.shutdownNow();
        executor.awaitTermination(2, TimeUnit.SECONDS);
    }

    private record Attributes(String username, String password) {
    }
}
