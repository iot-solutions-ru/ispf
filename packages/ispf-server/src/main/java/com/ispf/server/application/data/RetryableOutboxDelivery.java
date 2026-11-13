package com.ispf.server.application.data;

/**
 * The receiver is down or asked us to try again. The row stays pending and is not dead-lettered.
 */
public class RetryableOutboxDelivery extends RuntimeException {

    public RetryableOutboxDelivery(String message) {
        super(message);
    }
}
