package com.ispf.server.driver;

/**
 * Rejects driver writes when the command was issued under a stale lease fencing epoch.
 */
public final class DriverWriteFence {

    public boolean allowWrite(long commandEpoch, long currentEpoch) {
        return commandEpoch == currentEpoch && currentEpoch > 0;
    }
}
