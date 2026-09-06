package com.ispf.driver.grpc;

/** Point mapping: Service/Method or Service/Method#field */
public record GrpcJsonPoint(String serviceMethod, String field) {

    public static GrpcJsonPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("gRPC-JSON point mapping is blank");
        }
        String trimmed = raw.trim();
        String serviceMethod = trimmed;
        String field = null;
        int hash = trimmed.indexOf('#');
        if (hash >= 0) {
            serviceMethod = trimmed.substring(0, hash).trim();
            field = trimmed.substring(hash + 1).trim();
            if (field.isEmpty()) {
                field = null;
            }
        }
        if (serviceMethod.isEmpty() || !serviceMethod.contains("/")) {
            throw new IllegalArgumentException("gRPC-JSON mapping must be Service/Method: " + raw);
        }
        return new GrpcJsonPoint(serviceMethod, field);
    }

    public String httpPath() {
        return serviceMethod.startsWith("/") ? serviceMethod : "/" + serviceMethod;
    }

    public boolean hasField() {
        return field != null && !field.isBlank();
    }
}
