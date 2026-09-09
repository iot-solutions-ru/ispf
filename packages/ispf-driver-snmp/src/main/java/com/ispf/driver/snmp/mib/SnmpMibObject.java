package com.ispf.driver.snmp.mib;

/**
 * One OBJECT-TYPE (or OBJECT IDENTIFIER used as parent) from a MIB module.
 */
public final class SnmpMibObject {

    public enum Kind {
        SCALAR,
        COLUMN,
        TABLE,
        OID_NODE
    }

    private final String moduleName;
    private final String name;
    private final String oidExpression;
    private final String syntax;
    private final String maxAccess;
    private final String units;
    private final String description;
    private final Kind kind;
    private String resolvedOid = "";

    public SnmpMibObject(
            String moduleName,
            String name,
            String oidExpression,
            String syntax,
            String maxAccess,
            String units,
            String description,
            Kind kind
    ) {
        this.moduleName = moduleName;
        this.name = name;
        this.oidExpression = oidExpression;
        this.syntax = syntax != null ? syntax : "";
        this.maxAccess = maxAccess != null ? maxAccess : "";
        this.units = units != null ? units : "";
        this.description = description != null ? description : "";
        this.kind = kind != null ? kind : Kind.SCALAR;
    }

    public String moduleName() {
        return moduleName;
    }

    public String name() {
        return name;
    }

    public String qualifiedName() {
        return moduleName + "::" + name;
    }

    public String oidExpression() {
        return oidExpression;
    }

    public String syntax() {
        return syntax;
    }

    public String maxAccess() {
        return maxAccess;
    }

    public String units() {
        return units;
    }

    public String description() {
        return description;
    }

    public Kind kind() {
        return kind;
    }

    public String resolvedOid() {
        return resolvedOid;
    }

    void setResolvedOid(String oid) {
        this.resolvedOid = oid != null ? oid : "";
    }

    public boolean selectable() {
        return kind == Kind.SCALAR || kind == Kind.COLUMN;
    }
}
