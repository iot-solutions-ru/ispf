package com.ispf.server.spi;

import java.util.Optional;

/**
 * Whether a running device driver session is connected. Implemented by the driver runtime.
 */
public interface DriverConnectionLookup {

    Optional<Boolean> connected(String devicePath);
}
