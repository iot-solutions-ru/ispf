package com.ispf.driver.snmp;

import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.MessageException;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.mp.StatusInformation;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class SnmpLoopbackAgent implements AutoCloseable {

    private final TransportMapping<UdpAddress> transport;
    private final Snmp snmp;
    private final Map<OID, Variable> values = new ConcurrentHashMap<>();

    SnmpLoopbackAgent(int port) throws IOException {
        transport = new DefaultUdpTransportMapping(new UdpAddress("127.0.0.1/" + port));
        snmp = new Snmp(transport);
        snmp.addCommandResponder(respond());
        transport.listen();
    }

    void setValue(String oid, Variable value) {
        values.put(new OID(oid), value);
    }

    private CommandResponder respond() {
        return new CommandResponder() {
            @Override
            public <A extends org.snmp4j.smi.Address> void processPdu(CommandResponderEvent<A> event) {
                PDU request = event.getPDU();
                if (request == null) {
                    return;
                }
                PDU response = (PDU) request.clone();
                response.setType(PDU.RESPONSE);
                response.setErrorStatus(PDU.noError);
                for (VariableBinding binding : response.getVariableBindings()) {
                    if (request.getType() == PDU.SET) {
                        values.put(binding.getOid(), binding.getVariable());
                    }
                    Variable value = values.get(binding.getOid());
                    if (value != null) {
                        binding.setVariable(value);
                    }
                }
                try {
                    event.getMessageDispatcher().returnResponsePdu(
                            event.getMessageProcessingModel(),
                            event.getSecurityModel(),
                            event.getSecurityName(),
                            event.getSecurityLevel(),
                            response,
                            event.getMaxSizeResponsePDU(),
                            event.getStateReference(),
                            new StatusInformation()
                    );
                } catch (MessageException ignored) {
                    // best effort for tests
                }
                event.setProcessed(true);
            }
        };
    }

    @Override
    public void close() throws IOException {
        snmp.close();
    }
}
