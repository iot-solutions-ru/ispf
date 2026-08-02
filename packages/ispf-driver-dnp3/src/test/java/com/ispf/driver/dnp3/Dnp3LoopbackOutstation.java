package com.ispf.driver.dnp3;

import com.ispf.driver.dnp3.codec.Dnp3TcpCodec;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Minimal ISPF-owned DNP3 outstation for loopback integration tests.
 */
final class Dnp3LoopbackOutstation implements AutoCloseable {

    private final int port;
    private final int masterAddress;
    private final int outstationAddress;
    private final CountDownLatch bound = new CountDownLatch(1);
    private final ServerSocket serverSocket;
    private final Thread serverThread;
    private volatile boolean running = true;

    Dnp3LoopbackOutstation(int masterAddress, int outstationAddress) throws Exception {
        this.masterAddress = masterAddress;
        this.outstationAddress = outstationAddress;
        serverSocket = new ServerSocket(0);
        port = serverSocket.getLocalPort();
        serverThread = new Thread(this::serve, "dnp3-loopback-outstation");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    int port() {
        return port;
    }

    void awaitBound(long timeoutMs) throws InterruptedException {
        bound.await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    private void serve() {
        bound.countDown();
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                Thread clientThread = new Thread(() -> handleClient(socket), "dnp3-loopback-client");
                clientThread.setDaemon(true);
                clientThread.start();
            } catch (Exception ex) {
                if (running) {
                    throw new IllegalStateException("DNP3 loopback accept failed", ex);
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        try (socket) {
            while (!socket.isClosed()) {
                Dnp3TcpCodec.Frame request = Dnp3TcpCodec.readFrame(socket.getInputStream());
                if (request.source() != masterAddress || request.destination() != outstationAddress) {
                    continue;
                }
                int sequence = Dnp3TcpCodec.requestSequence(request);
                byte[] response = Dnp3TcpCodec.integrityPollResponse(
                        outstationAddress,
                        masterAddress,
                        seedMeasurements(),
                        sequence
                );
                Dnp3TcpCodec.writeFrame(socket.getOutputStream(), response);
            }
        } catch (Exception ignored) {
            // Client disconnects end the test session.
        }
    }

    private static List<Dnp3TcpCodec.Measurement> seedMeasurements() {
        return List.of(
                new Dnp3TcpCodec.Measurement(Dnp3Point.Dnp3DataType.ANALOG_INPUT, 0, 12.34, 0x01),
                new Dnp3TcpCodec.Measurement(Dnp3Point.Dnp3DataType.BINARY_INPUT, 0, true, 0x01),
                new Dnp3TcpCodec.Measurement(Dnp3Point.Dnp3DataType.COUNTER, 0, 999L, 0x01),
                new Dnp3TcpCodec.Measurement(Dnp3Point.Dnp3DataType.ANALOG_OUTPUT, 0, 55.5, 0x01),
                new Dnp3TcpCodec.Measurement(Dnp3Point.Dnp3DataType.BINARY_OUTPUT, 0, false, 0x01)
        );
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
