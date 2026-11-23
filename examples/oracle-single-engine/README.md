# Oracle single-engine (B2) example

Opt-in profile: configuration + JDBC historian + JDBC journal on one Oracle Database Free `ISPF_DB`.

```
ISPF_METADATA_DB_KIND=oracle
ISPF_ORACLE_SINGLE_ENGINE=true
ISPF_VARIABLE_HISTORY_ENABLED=true
ISPF_VARIABLE_HISTORY_STORE=jdbc
ISPF_EVENT_JOURNAL_ENABLED=true
ISPF_EVENT_JOURNAL_STORE=jdbc
ISPF_CLUSTER_ENABLED=false
SPRING_JPA_HIBERNATE_DDL_AUTO=none
SPRING_DATASOURCE_DRIVER_CLASS_NAME=oracle.jdbc.OracleDriver
```

Add `ojdbc17.jar` on `loader.path` (ISPF does not ship it). Set the flag explicitly; the `oracle-single-engine` deploy profile alone may not flip it when `application.yml` already defines the property.

See [oracle-b2-sku-promotion-checklist](../../docs/en/oracle-b2-sku-promotion-checklist.md).
