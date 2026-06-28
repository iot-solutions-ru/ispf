package com.ispf.driver.dnp3;

import com.ispf.driver.DriverException;
import io.stepfunc.dnp3.AssociationConfig;
import io.stepfunc.dnp3.AssociationId;
import io.stepfunc.dnp3.AssociationInformation;
import io.stepfunc.dnp3.Classes;
import io.stepfunc.dnp3.ClientState;
import io.stepfunc.dnp3.ConnectStrategy;
import io.stepfunc.dnp3.EndpointList;
import io.stepfunc.dnp3.EventClasses;
import io.stepfunc.dnp3.FunctionCode;
import io.stepfunc.dnp3.LinkErrorMode;
import io.stepfunc.dnp3.MasterChannel;
import io.stepfunc.dnp3.MasterChannelConfig;
import io.stepfunc.dnp3.Request;
import io.stepfunc.dnp3.Runtime;
import io.stepfunc.dnp3.RuntimeConfig;
import io.stepfunc.dnp3.TaskError;
import io.stepfunc.dnp3.TaskType;
import io.stepfunc.dnp3.UtcTimestamp;
import org.joou.UByte;
import org.joou.UShort;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.joou.Unsigned.ulong;
import static org.joou.Unsigned.ushort;

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

    private Runtime runtime;
    private MasterChannel channel;
    private AssociationId associationId;
    private volatile ClientState clientState = ClientState.DISABLED;

    Dnp3MasterSession(String host, int port, int masterAddress, int outstationAddress, int timeoutMs) {
        this.host = host;
        this.port = port;
        this.masterAddress = masterAddress;
        this.outstationAddress = outstationAddress;
        this.timeoutMs = timeoutMs;
    }

    void connect() throws DriverException {
        try {
            runtime = new Runtime(new RuntimeConfig());
            CountDownLatch connected = new CountDownLatch(1);
            AtomicReference<ClientState> lastState = new AtomicReference<>(ClientState.DISABLED);

            channel = MasterChannel.createTcpChannel(
                    runtime,
                    LinkErrorMode.CLOSE,
                    new MasterChannelConfig(ushort(masterAddress)),
                    new EndpointList(host + ":" + port),
                    new ConnectStrategy(),
                    state -> {
                        clientState = state;
                        lastState.set(state);
                        if (state == ClientState.CONNECTED) {
                            connected.countDown();
                        }
                    }
            );

            AssociationConfig associationConfig = new AssociationConfig(
                    EventClasses.none(),
                    EventClasses.none(),
                    Classes.all(),
                    EventClasses.none()
            ).withResponseTimeout(Duration.ofMillis(timeoutMs));

            associationId = channel.addAssociation(
                    ushort(outstationAddress),
                    associationConfig,
                    readCache,
                    () -> UtcTimestamp.valid(ulong(System.currentTimeMillis())),
                    new AssociationInformation() {
                        @Override
                        public void taskStart(TaskType taskType, FunctionCode functionCode, UByte seq) {
                        }

                        @Override
                        public void taskSuccess(TaskType taskType, FunctionCode functionCode, UByte seq) {
                        }

                        @Override
                        public void taskFail(TaskType taskType, TaskError error) {
                        }

                        @Override
                        public void unsolicitedResponse(boolean isDuplicate, UByte seq) {
                        }
                    }
            );

            channel.enable();
            if (!connected.await(timeoutMs, TimeUnit.MILLISECONDS) && lastState.get() != ClientState.CONNECTED) {
                throw new DriverException("DNP3 TCP connect timeout to " + host + ":" + port);
            }
        } catch (DriverException ex) {
            close();
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            close();
            throw new DriverException("DNP3 connect interrupted", ex);
        } catch (Exception ex) {
            close();
            throw new DriverException("DNP3 master session failed", ex);
        }
    }

    boolean isConnected() {
        return channel != null && clientState == ClientState.CONNECTED;
    }

    void pollAllClasses() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        readCache.clear();
        Request request = Request.classRequest(true, true, true, true);
        try {
            CompletionStage<?> stage = channel.read(associationId, request);
            stage.toCompletableFuture().get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            throw new DriverException("DNP3 Class 0/1/2/3 poll timeout", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DriverException("DNP3 poll interrupted", ex);
        } catch (Exception ex) {
            throw new DriverException("DNP3 Class 0/1/2/3 poll failed", ex);
        }
    }

    Dnp3ReadCache cache() {
        return readCache;
    }

    @Override
    public void close() {
        if (channel != null) {
            try {
                channel.disable();
            } catch (Exception ignored) {
                // best effort
            }
            try {
                channel.shutdown();
            } catch (Exception ignored) {
                // best effort
            }
            channel = null;
            associationId = null;
        }
        if (runtime != null) {
            try {
                runtime.shutdown();
            } catch (Exception ignored) {
                // best effort
            }
            runtime = null;
        }
        clientState = ClientState.SHUTDOWN;
    }
}
