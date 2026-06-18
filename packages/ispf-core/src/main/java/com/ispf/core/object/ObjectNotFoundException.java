package com.ispf.core.object;

/**
 * Thrown when an object path does not exist in the tree.
 */
public class ObjectNotFoundException extends RuntimeException {

    private final String path;

    public ObjectNotFoundException(String path) {
        super("Object not found: " + path);
        this.path = path;
    }

    public String path() {
        return path;
    }
}
