package com.ispf.driver.iec104.codec;

import java.io.IOException;

public interface Iec104ConnectionListener {

    void onAsdu(Iec104Connection connection, Iec104Asdu asdu);

    default void onConnectionClosed(Iec104Connection connection, IOException cause) {
    }

    default void onDataTransferStateChanged(Iec104Connection connection, boolean active) {
    }
}
