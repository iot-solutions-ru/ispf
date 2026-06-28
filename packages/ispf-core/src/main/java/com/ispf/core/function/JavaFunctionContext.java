package com.ispf.core.function;

/**
 * Invocation context for object-tree Java functions ({@code sourceType=java}).
 */
public record JavaFunctionContext(String objectPath, String functionName) {
}
