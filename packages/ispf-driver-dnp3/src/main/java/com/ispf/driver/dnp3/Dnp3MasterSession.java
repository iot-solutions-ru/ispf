package com.ispf.driver.dnp3;

import com.ispf.driver.DriverException;
import com.ispf.driver.DriverPermanentException;
import com.ispf.driver.DriverTransientException;
import com.ispf.driver.dnp3.codec.Dnp3TcpCodec;
import com.ispf.driver.dnp3.codec.Dnp3TcpCodec.ControlCommand;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * DNP3 master channel session (TCP) with Class 0/1/2/3 integrity poll support.
 */
final class Dnp3MasterSession implements AutoCloseable {

    private final String host;
    private final int port;
    private final int masterAddress;
    private final int outstationAddress;
    private final int timeoutMs;
    private final Dnp3ReadCache readCache = new Dnp3ReadCache();

    private Socket socket;
    private int sequence;

    Dnp3MasterSession(String host, int port, int masterAddress, int outstationAddress, int timeoutMs) {
        this.host = host;
        this.port = port;
        this.masterAddress = masterAddress;
        this.outstationAddress = outstationAddress;
        this.timeoutMs = timeoutMs;
    }

    synchronized void connect() throws DriverException {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
        } catch (Exception ex) {
            close();
            throw new DriverTransientException("DNP3 master session failed", ex);
        }
    }

    synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    synchronized void pollAllClasses() throws DriverException {
        if (!isConnected()) {
            throw new DriverTransientException("Not connected");
        }
        readCache.clear();
        try {
            int pollSequence = sequence++ & 0x0F;
            byte[] request = Dnp3TcpCodec.integrityPollRequest(masterAddress, outstationAddress, pollSequence);
            Dnp3TcpCodec.writeFrame(socket.getOutputStream(), request);
            Dnp3TcpCodec.Frame response = Dnp3TcpCodec.readFrame(socket.getInputStream());
            if (response.source() != outstationAddress || response.destination() != masterAddress) {
                throw new DriverPermanentException("DNP3 response address mismatch");
            }
            Dnp3TcpCodec.applyResponse(response, readCache);
        } catch (Exception ex) {
            throw new DriverTransientException("DNP3 Class 0/1/2/3 poll failed", ex);
        }
    }

    Dnp3ReadCache cache() {
        return readCache;
    }

    synchronized void selectBeforeOperate(Dnp3Point point, Object value) throws DriverException {
        ControlCommand command = new ControlCommand(point.dataType(), point.index(), value);
        exchangeControl(command, true);
        exchangeControl(command, false);
    }

    synchronized void directOperate(Dnp3Point point, Object value) throws DriverException {
        exchangeControl(new ControlCommand(point.dataType(), point.index(), value), false);
    }

    private void exchangeControl(ControlCommand command, boolean select) throws DriverException {
        if (!isConnected()) {
            throw new DriverTransientException("Not connected");
        }
        try {
            int operateSequence = sequence++ & 0x0F;
            byte[] request;
            if (select) {
                request = Dnp3TcpCodec.selectRequest(masterAddress, outstationAddress, operateSequence, command);
                operateArmed = true;
            } else if (operateArmed) {
                request = Dnp3TcpCodec.operateRequest(masterAddress, outstationAddress, operateSequence, command);
                operateArmed = false;
            } else {
                request = Dnp3TcpCodec.directOperateRequest(masterAddress, outstationAddress, operateSequence, command);
            }
            Dnp3TcpCodec.writeFrame(socket.getOutputStream(), request);
            Dnp3TcpCodec.Frame response = Dnp3TcpCodec.readFrame(socket.getInputStream());
            if (response.source() != outstationAddress || response.destination() != masterAddress) {
                throw new DriverPermanentException("DNP3 response address mismatch");
            }
            if (Dnp3TcpCodec.applicationSequence(response) != operateSequence) {
                throw new DriverPermanentException("DNP3 operate response sequence mismatch");
            }
            ControlCommand echoed = Dnp3TcpCodec.parseOperateResponse(response);
            if (echoed.type() != command.type() || echoed.index() != command.index()
                    || !sameValue(command.value(), echoed.value())) {
                throw new DriverPermanentException("DNP3 control echo mismatch");
            }
        } catch (DriverException ex) {
            operateArmed = false;
            throw ex;
        } catch (Exception ex) {
            operateArmed = false;
            throw new DriverTransientException(select ? "DNP3 select failed" : "DNP3 operate failed", ex);
        }
    }

    private static boolean sameValue(Object expected, Object actual) {
        if (expected instanceof Boolean bool) {
            return bool.equals(actual);
        }
        if (expected instanceof Number expectedNumber && actual instanceof Number actualNumber) {
            return Float.compare(expectedNumber.floatValue(), actualNumber.floatValue()) == 0;
        }
        return false;
    }

    private boolean operateArmed;

    @Override
    public synchronized void close() {
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {
                // best effort
            }
            socket = null;
        }
    }
}
