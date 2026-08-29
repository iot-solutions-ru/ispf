package com.ispf.driver.thread;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Thread protocol stub (thread).
 * <p>
 * Thread Border Router stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class ThreadDeviceDriver extends ProtocolStubDeviceDriver {

    public ThreadDeviceDriver() {
        super(
                "thread",
                "Thread Driver",
                "Thread Border Router stub",
                8081
        );
    }
}
