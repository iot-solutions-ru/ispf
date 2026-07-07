package com.ispf.server.datasource;

public record ConnectionTestResult(boolean connected, String message) {

    public static ConnectionTestResult ok() {
        return new ConnectionTestResult(true, null);
    }

    public static ConnectionTestResult failed(String message) {
        String detail = message != null && !message.isBlank() ? message : "Connection failed";
        return new ConnectionTestResult(false, detail);
    }
}
