package com.ispf.driver.dlms;

import gurux.common.IGXMediaListener;
import gurux.common.MediaStateEventArgs;
import gurux.common.PropertyChangedEventArgs;
import gurux.common.ReceiveEventArgs;
import gurux.common.TraceEventArgs;
import gurux.dlms.GXDLMSConnectionEventArgs;
import gurux.dlms.GXDLMSServer2;
import gurux.dlms.GXServerReply;
import gurux.dlms.ValueEventArgs;
import gurux.dlms.enums.AccessMode;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.Conformance;
import gurux.dlms.enums.InterfaceType;
import gurux.dlms.enums.MethodAccessMode;
import gurux.dlms.enums.ObjectType;
import gurux.dlms.enums.SourceDiagnostic;
import gurux.dlms.objects.GXDLMSAssociationLogicalName;
import gurux.dlms.objects.GXDLMSData;
import gurux.dlms.objects.GXDLMSObject;
import gurux.dlms.objects.GXDLMSRegister;
import gurux.dlms.objects.GXDLMSTcpUdpSetup;
import gurux.net.GXNet;
import gurux.net.enums.NetworkType;

import java.net.ServerSocket;

/**
 * Minimal DLMS WRAPPER outstation for integration tests.
 */
final class DlmsLoopbackServer extends GXDLMSServer2 implements IGXMediaListener, gurux.net.IGXNetListener {

    static final String ENERGY_OBIS = "1.0.1.8.0.255";

    private final int clientSap;
    private final GXDLMSRegister energyRegister;
    private final GXNet media;

    DlmsLoopbackServer(int clientSap) throws Exception {
        super(createAssociation(clientSap), new GXDLMSTcpUdpSetup());
        this.clientSap = clientSap;
        setMaxReceivePDUSize(1024);

        GXDLMSData deviceName = new GXDLMSData("0.0.42.0.0.255");
        deviceName.setValue("ISPF-TEST");
        deviceName.setAccess(2, AccessMode.READ);
        getItems().add(deviceName);

        energyRegister = new GXDLMSRegister(ENERGY_OBIS);
        energyRegister.setValue(42.0);
        energyRegister.setAccess(2, AccessMode.READ_WRITE);
        getItems().add(energyRegister);

        GXDLMSAssociationLogicalName association = (GXDLMSAssociationLogicalName) getItems()
                .findByLN(ObjectType.ASSOCIATION_LOGICAL_NAME, "0.0.40.0.1.255");
        association.getObjectList().clear();
        association.getObjectList().add(association);
        association.getObjectList().add(deviceName);
        association.getObjectList().add(energyRegister);

        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        media = new GXNet(NetworkType.TCP, "127.0.0.1", port);
        media.setServer(true);
        media.addListener(this);
        media.open();
        initialize();
    }

    int port() {
        return media.getPort();
    }

    double energyValue() {
        return ((Number) energyRegister.getValue()).doubleValue();
    }

    private static GXDLMSAssociationLogicalName createAssociation(int clientSap) {
        GXDLMSAssociationLogicalName association = new GXDLMSAssociationLogicalName("0.0.40.0.1.255");
        association.setClientSAP(clientSap);
        association.getAuthenticationMechanismName().setMechanismId(Authentication.NONE);
        association.getXDLMSContextInfo().getConformance().clear();
        association.getXDLMSContextInfo().getConformance().add(Conformance.GET);
        association.getXDLMSContextInfo().getConformance().add(Conformance.SET);
        association.getXDLMSContextInfo().setMaxReceivePduSize(1024);
        association.getXDLMSContextInfo().setMaxSendPduSize(1024);
        return association;
    }

    @Override
    protected boolean isTarget(int serverAddress, int clientAddress) {
        if (clientAddress != clientSap) {
            return false;
        }
        setAssignedAssociation((GXDLMSAssociationLogicalName) getItems()
                .findByLN(ObjectType.ASSOCIATION_LOGICAL_NAME, "0.0.40.0.1.255"));
        return true;
    }

    @Override
    protected SourceDiagnostic onValidateAuthentication(Authentication authentication, byte[] password) {
        return authentication == Authentication.NONE ? SourceDiagnostic.NONE : SourceDiagnostic.AUTHENTICATION_FAILURE;
    }

    @Override
    public void onPreGet(ValueEventArgs[] args) {
    }

    @Override
    public void onPostGet(ValueEventArgs[] args) {
    }

    @Override
    protected GXDLMSObject onFindObject(ObjectType objectType, int sn, String ln) {
        return getItems().findByLN(objectType, ln);
    }

    @Override
    public void onPreRead(ValueEventArgs[] args) {
    }

    @Override
    public void onPostRead(ValueEventArgs[] args) {
    }

    @Override
    protected void onPreWrite(ValueEventArgs[] args) {
    }

    @Override
    protected void onPostWrite(ValueEventArgs[] args) {
    }

    @Override
    protected void onConnected(GXDLMSConnectionEventArgs connectionInfo) {
    }

    @Override
    protected void onInvalidConnection(GXDLMSConnectionEventArgs connectionInfo) {
    }

    @Override
    protected void onDisconnected(GXDLMSConnectionEventArgs connectionInfo) {
    }

    @Override
    protected AccessMode onGetAttributeAccess(ValueEventArgs arg) {
        return arg.getTarget().getAccess(arg.getIndex());
    }

    @Override
    protected MethodAccessMode onGetMethodAccess(ValueEventArgs arg) {
        return MethodAccessMode.NO_ACCESS;
    }

    @Override
    protected void onPreAction(ValueEventArgs[] args) {
    }

    @Override
    protected void onPostAction(ValueEventArgs[] args) {
    }

    @Override
    public void onReceived(Object sender, ReceiveEventArgs e) {
        try {
            synchronized (this) {
                GXServerReply reply = new GXServerReply((byte[]) e.getData());
                do {
                    handleRequest(reply);
                    if (reply.getReply() != null) {
                        media.send(reply.getReply(), e.getSenderInfo());
                        reply.setData(null);
                    }
                } while (reply.isStreaming());
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void onMediaStateChange(Object sender, MediaStateEventArgs e) {
    }

    @Override
    public void onClientConnected(Object sender, gurux.net.ConnectionEventArgs e) {
    }

    @Override
    public void onClientDisconnected(Object sender, gurux.net.ConnectionEventArgs e) {
    }

    @Override
    public void onTrace(Object sender, TraceEventArgs e) {
    }

    @Override
    public void onPropertyChanged(Object sender, PropertyChangedEventArgs e) {
    }

    @Override
    public void onError(Object sender, Exception ex) {
    }

    void closeServer() {
        try {
            media.close();
        } catch (Exception ignored) {
            // best effort
        }
    }
}
