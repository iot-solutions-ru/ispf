package com.ispf.core.object;

/**
 * Platform-managed storage for visual group membership lists.
 */
public final class VisualGroupConstants {

    public static final String MEMBERS_VARIABLE = "@groupMembers";

    private VisualGroupConstants() {
    }

    public static boolean isReservedVariable(String name) {
        return MEMBERS_VARIABLE.equals(name);
    }
}
