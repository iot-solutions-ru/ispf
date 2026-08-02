package com.ispf.driver.bacnet.codec;

public class BacnetException extends Exception {

    public BacnetException(String message) {
        super(message);
    }

    public BacnetException(String message, Throwable cause) {
        super(message, cause);
    }
}
