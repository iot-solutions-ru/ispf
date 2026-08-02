package com.ispf.driver.iec104.codec;

import java.io.IOException;

public interface Iec104ServerListener {

    Iec104ConnectionListener onConnection(Iec104Connection connection);

    default void onStopped(IOException cause) {
    }

    default void onConnectionAttemptFailed(IOException cause) {
    }
}
