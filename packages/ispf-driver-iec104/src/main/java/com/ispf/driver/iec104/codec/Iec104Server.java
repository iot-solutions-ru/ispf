package com.ispf.driver.iec104.codec;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Iec104Server implements AutoCloseable {

    private final int port;
    private final Set<Iec104Connection> connections = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean stopped = new AtomicBoolean(true);
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public Iec104Server(int port) {
        this.port = port;
    }

    public void start(Iec104ServerListener listener) throws IOException {
        serverSocket = new ServerSocket(port);
        stopped.set(false);
        acceptThread = new Thread(() -> acceptLoop(listener), "iec104-server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public boolean isStopped() {
        return stopped.get();
    }

    @Override
    public void close() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        for (Iec104Connection connection : connections) {
            try {
                connection.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
        connections.clear();
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // best effort
            }
            serverSocket = null;
        }
    }

    private void acceptLoop(Iec104ServerListener listener) {
        IOException stoppedCause = null;
        try {
            while (!stopped.get()) {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                registerConnection(socket, listener);
            }
        } catch (IOException e) {
            if (!stopped.get()) {
                stoppedCause = e;
                listener.onConnectionAttemptFailed(e);
            }
        } finally {
            close();
            listener.onStopped(stoppedCause);
        }
    }

    private void registerConnection(Socket socket, Iec104ServerListener listener) {
        try {
            DelegatingConnectionListener delegate = new DelegatingConnectionListener();
            Iec104Connection connection = new Iec104Connection(socket, delegate);
            connections.add(connection);
            delegate.connection = connection;
            delegate.target = listener.onConnection(connection);
        } catch (IOException e) {
            listener.onConnectionAttemptFailed(e);
            try {
                socket.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    private final class DelegatingConnectionListener implements Iec104ConnectionListener {

        private Iec104Connection connection;
        private Iec104ConnectionListener target;

        @Override
        public void onAsdu(Iec104Connection ignored, Iec104Asdu asdu) {
            Iec104ConnectionListener current = target;
            if (current != null) {
                current.onAsdu(connection, asdu);
            }
        }

        @Override
        public void onConnectionClosed(Iec104Connection ignored, IOException cause) {
            connections.remove(connection);
            Iec104ConnectionListener current = target;
            if (current != null) {
                current.onConnectionClosed(connection, cause);
            }
        }

        @Override
        public void onDataTransferStateChanged(Iec104Connection ignored, boolean active) {
            Iec104ConnectionListener current = target;
            if (current != null) {
                current.onDataTransferStateChanged(connection, active);
            }
        }
    }
}
