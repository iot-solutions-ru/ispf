package com.ispf.server.driver;

/**
 * How far a driver's behavior has been checked.
 * {@link #FIELD} is a named customer-plant gate. Lab loopback is not that gate.
 */
public enum DriverEvidence {
    LOOPBACK_VERIFIED,
    FIELD
}
