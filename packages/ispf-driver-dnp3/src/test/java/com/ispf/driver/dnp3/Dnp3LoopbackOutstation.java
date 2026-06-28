package com.ispf.driver.dnp3;

import io.stepfunc.dnp3.AddressFilter;
import io.stepfunc.dnp3.AnalogInput;
import io.stepfunc.dnp3.AnalogInputConfig;
import io.stepfunc.dnp3.AnalogOutputStatus;
import io.stepfunc.dnp3.AnalogOutputStatusConfig;
import io.stepfunc.dnp3.ApplicationIin;
import io.stepfunc.dnp3.BinaryInput;
import io.stepfunc.dnp3.BinaryInputConfig;
import io.stepfunc.dnp3.BinaryOutputStatus;
import io.stepfunc.dnp3.BinaryOutputStatusConfig;
import io.stepfunc.dnp3.CommandStatus;
import io.stepfunc.dnp3.ControlHandler;
import io.stepfunc.dnp3.Counter;
import io.stepfunc.dnp3.CounterConfig;
import io.stepfunc.dnp3.Database;
import io.stepfunc.dnp3.DatabaseHandle;
import io.stepfunc.dnp3.EventBufferConfig;
import io.stepfunc.dnp3.EventClass;
import io.stepfunc.dnp3.Flags;
import io.stepfunc.dnp3.Group12Var1;
import io.stepfunc.dnp3.LinkErrorMode;
import io.stepfunc.dnp3.OperateType;
import io.stepfunc.dnp3.Outstation;
import io.stepfunc.dnp3.OutstationApplication;
import io.stepfunc.dnp3.OutstationConfig;
import io.stepfunc.dnp3.OutstationInformation;
import io.stepfunc.dnp3.OutstationServer;
import io.stepfunc.dnp3.RequestHeader;
import io.stepfunc.dnp3.RestartDelay;
import io.stepfunc.dnp3.Runtime;
import io.stepfunc.dnp3.RuntimeConfig;
import io.stepfunc.dnp3.StaticAnalogInputVariation;
import io.stepfunc.dnp3.Timestamp;
import io.stepfunc.dnp3.UpdateOptions;
import io.stepfunc.dnp3.WriteTimeResult;
import org.joou.UShort;
import org.joou.ULong;

import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.joou.Unsigned.ubyte;
import static org.joou.Unsigned.uint;
import static org.joou.Unsigned.ulong;
import static org.joou.Unsigned.ushort;

/**
 * Minimal DNP3 outstation for loopback integration tests.
 */
final class Dnp3LoopbackOutstation implements AutoCloseable {

    private final int port;
    private final int masterAddress;
    private final int outstationAddress;
    private final CountDownLatch bound = new CountDownLatch(1);

    private Runtime runtime;
    private OutstationServer server;
    private Outstation outstation;

    Dnp3LoopbackOutstation(int masterAddress, int outstationAddress) throws Exception {
        this.masterAddress = masterAddress;
        this.outstationAddress = outstationAddress;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        bound.countDown();
        start();
    }

    int port() {
        return port;
    }

    void awaitBound(long timeoutMs) throws InterruptedException {
        bound.await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    private void start() {
        runtime = new Runtime(new RuntimeConfig());
        server = OutstationServer.createTcpServer(runtime, LinkErrorMode.CLOSE, "127.0.0.1:" + port);
        OutstationConfig config = new OutstationConfig(
                ushort(outstationAddress),
                ushort(masterAddress),
                EventBufferConfig.noEvents()
        );
        outstation = server.addOutstation(
                config,
                new OutstationApplication() {
                    @Override
                    public UShort getProcessingDelayMs() {
                        return ushort(0);
                    }

                    @Override
                    public WriteTimeResult writeAbsoluteTime(ULong time) {
                        return WriteTimeResult.NOT_SUPPORTED;
                    }

                    @Override
                    public ApplicationIin getApplicationIin() {
                        return new ApplicationIin();
                    }

                    @Override
                    public RestartDelay coldRestart() {
                        return RestartDelay.notSupported();
                    }

                    @Override
                    public RestartDelay warmRestart() {
                        return RestartDelay.notSupported();
                    }
                },
                new OutstationInformation() {
                    @Override
                    public void processRequestFromIdle(RequestHeader header) {
                    }

                    @Override
                    public void broadcastReceived(io.stepfunc.dnp3.FunctionCode functionCode,
                                                  io.stepfunc.dnp3.BroadcastAction broadcastAction) {
                    }

                    @Override
                    public void enterSolicitedConfirmWait(org.joou.UByte seq) {
                    }

                    @Override
                    public void solicitedConfirmTimeout(org.joou.UByte seq) {
                    }

                    @Override
                    public void solicitedConfirmReceived(org.joou.UByte seq) {
                    }

                    @Override
                    public void solicitedConfirmWaitNewRequest() {
                    }

                    @Override
                    public void wrongSolicitedConfirmSeq(org.joou.UByte seq, org.joou.UByte expected) {
                    }

                    @Override
                    public void unexpectedConfirm(boolean unsolicited, org.joou.UByte seq) {
                    }

                    @Override
                    public void enterUnsolicitedConfirmWait(org.joou.UByte seq) {
                    }

                    @Override
                    public void unsolicitedConfirmTimeout(org.joou.UByte seq, boolean retry) {
                    }

                    @Override
                    public void unsolicitedConfirmed(org.joou.UByte seq) {
                    }

                    @Override
                    public void clearRestartIin() {
                    }
                },
                new ControlHandler() {
                    @Override
                    public void beginFragment() {
                    }

                    @Override
                    public void endFragment(DatabaseHandle database) {
                    }

                    @Override
                    public CommandStatus selectG12v1(Group12Var1 command, UShort index, DatabaseHandle database) {
                        return CommandStatus.NOT_SUPPORTED;
                    }

                    @Override
                    public CommandStatus operateG12v1(Group12Var1 command, UShort index, OperateType opType,
                                                      DatabaseHandle database) {
                        return CommandStatus.NOT_SUPPORTED;
                    }

                    @Override
                    public CommandStatus selectG41v1(int value, UShort index, DatabaseHandle database) {
                        return CommandStatus.NOT_SUPPORTED;
                    }

                    @Override
                    public CommandStatus operateG41v1(int value, UShort index, OperateType opType,
                                                      DatabaseHandle database) {
                        return CommandStatus.NOT_SUPPORTED;
                    }

                    @Override
                    public CommandStatus selectG41v2(short value, UShort index, DatabaseHandle database) {
                        return CommandStatus.NOT_SUPPORTED;
                    }

                    @Override
                    public CommandStatus operateG41v2(short value, UShort index, OperateType opType,
                                                      DatabaseHandle database) {
                        return CommandStatus.NOT_SUPPORTED;
                    }

                    @Override
                    public CommandStatus selectG41v3(float value, UShort index, DatabaseHandle database) {
                        return CommandStatus.NOT_SUPPORTED;
                    }

                    @Override
                    public CommandStatus operateG41v3(float value, UShort index, OperateType opType,
                                                      DatabaseHandle database) {
                        return CommandStatus.NOT_SUPPORTED;
                    }

                    @Override
                    public CommandStatus selectG41v4(double value, UShort index, DatabaseHandle database) {
                        return CommandStatus.NOT_SUPPORTED;
                    }

                    @Override
                    public CommandStatus operateG41v4(double value, UShort index, OperateType opType,
                                                      DatabaseHandle database) {
                        return CommandStatus.NOT_SUPPORTED;
                    }
                },
                state -> {
                },
                AddressFilter.any()
        );
        outstation.transaction(database -> seedDatabase(database));
        outstation.enable();
        server.bind();
    }

    private static void seedDatabase(Database database) {
        Flags online = new Flags(ubyte(1));
        Timestamp time = Timestamp.unsynchronizedTimestamp(ulong(System.currentTimeMillis()));

        database.addAnalogInput(ushort(0), EventClass.NONE, new AnalogInputConfig()
                .withStaticVariation(StaticAnalogInputVariation.GROUP30_VAR5));
        database.updateAnalogInput(
                new AnalogInput(ushort(0), 12.34, online, time),
                UpdateOptions.noEvent()
        );

        database.addBinaryInput(ushort(0), EventClass.NONE, new BinaryInputConfig());
        database.updateBinaryInput(
                new BinaryInput(ushort(0), true, online, time),
                UpdateOptions.noEvent()
        );

        database.addCounter(ushort(0), EventClass.NONE, new CounterConfig());
        database.updateCounter(
                new Counter(ushort(0), uint(999), online, time),
                UpdateOptions.noEvent()
        );

        database.addAnalogOutputStatus(ushort(0), EventClass.NONE, new AnalogOutputStatusConfig());
        database.updateAnalogOutputStatus(
                new AnalogOutputStatus(ushort(0), 55.5, online, time),
                UpdateOptions.noEvent()
        );

        database.addBinaryOutputStatus(ushort(0), EventClass.NONE, new BinaryOutputStatusConfig());
        database.updateBinaryOutputStatus(
                new BinaryOutputStatus(ushort(0), false, online, time),
                UpdateOptions.noEvent()
        );
    }

    @Override
    public void close() {
        if (outstation != null) {
            try {
                outstation.disable();
            } catch (Exception ignored) {
                // best effort
            }
            outstation = null;
        }
        if (server != null) {
            try {
                server.shutdown();
            } catch (Exception ignored) {
                // best effort
            }
            server = null;
        }
        if (runtime != null) {
            try {
                runtime.shutdown();
            } catch (Exception ignored) {
                // best effort
            }
            runtime = null;
        }
    }
}
