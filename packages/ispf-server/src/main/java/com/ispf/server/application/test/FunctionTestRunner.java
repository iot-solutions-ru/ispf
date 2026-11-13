package com.ispf.server.application.test;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.application.data.ApplicationSchemaSupport;
import com.ispf.server.application.function.ApplicationFunctionRuntime;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.application.script.Ed25519FunctionSignatureVerifier;
import com.ispf.server.application.script.FunctionSignature;
import com.ispf.server.application.script.PlatformScriptBridge;
import com.ispf.server.object.ObjectManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import tools.jackson.databind.ObjectMapper;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runs solution smoke tests inside a transaction that can be marked rollback-only (W6).
 */
@Service
public class FunctionTestRunner {

    private final JdbcTemplate jdbcTemplate;
    private final ApplicationFunctionRuntime functionRuntime;
    private final ApplicationFunctionStore functionStore;
    private final ApplicationDataStore dataStore;
    private final ApplicationSchemaSession schemaSession;
    private final PlatformScriptBridge platformScriptBridge;
    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;
    private final Ed25519FunctionSignatureVerifier signatureVerifier;

    public FunctionTestRunner(
            JdbcTemplate jdbcTemplate,
            ApplicationFunctionRuntime functionRuntime,
            ApplicationFunctionStore functionStore,
            ApplicationDataStore dataStore,
            ApplicationSchemaSession schemaSession,
            PlatformScriptBridge platformScriptBridge,
            ObjectManager objectManager,
            ObjectMapper objectMapper,
            Ed25519FunctionSignatureVerifier signatureVerifier
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.functionRuntime = functionRuntime;
        this.functionStore = functionStore;
        this.dataStore = dataStore;
        this.schemaSession = schemaSession;
        this.platformScriptBridge = platformScriptBridge;
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
        this.signatureVerifier = signatureVerifier;
    }

    @Transactional
    public TestResult run(TestSpec spec) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> details = new LinkedHashMap<>();
        SecurityContext previous = SecurityContextHolder.getContext();
        boolean installedTestPrincipal = installBundleTestPrincipalIfNeeded(spec);
        try {
            if ("telemetry".equalsIgnoreCase(spec.kind())) {
                runTelemetry(spec, details, errors);
            } else {
                runFunction(spec, details, errors);
            }
        } catch (Exception ex) {
            errors.add(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            details.put("exception", ex.getClass().getSimpleName());
        } finally {
            if (installedTestPrincipal) {
                SecurityContextHolder.clearContext();
                if (previous.getAuthentication() != null) {
                    SecurityContextHolder.setContext(previous);
                }
            }
            if (spec.rollback()) {
                try {
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                } catch (org.springframework.transaction.NoTransactionException ignored) {
                    // Direct/unit calls without a Spring proxy have no TransactionStatus.
                }
            }
        }
        return new TestResult(
                spec.id(),
                spec.kind(),
                errors.isEmpty() ? "PASS" : "FAIL",
                errors,
                details
        );
    }

    /**
     * Rollback-only function smoke tests often exercise {@code signaturePolicy=REQUIRED}.
     * Deploy is authenticated in production, but the in-process runner may have no principal.
     * Use a fresh SecurityContext so the previous thread context is never mutated in place.
     */
    private static boolean installBundleTestPrincipalIfNeeded(TestSpec spec) {
        if (!spec.rollback() || !"function".equalsIgnoreCase(spec.kind())) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getName() != null
                && !authentication.getName().isBlank()) {
            return false;
        }
        SecurityContext testContext = SecurityContextHolder.createEmptyContext();
        testContext.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "bundle-test",
                        "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))
                )
        );
        SecurityContextHolder.setContext(testContext);
        return true;
    }

    private void runFunction(TestSpec spec, Map<String, Object> details, List<String> errors) throws Exception {
        if (isBlank(spec.objectPath()) || isBlank(spec.functionName())) {
            throw new IllegalArgumentException("objectPath and functionName are required");
        }
        var deployed = functionStore.findLatest(spec.objectPath(), spec.functionName())
                .orElseThrow(() -> new IllegalArgumentException("Deployed function missing: " + spec.functionName()));
        runFixtureSql(deployed.appId(), spec.fixtureSql());
        DataSchema inputSchema = objectMapper.readValue(deployed.inputSchemaJson(), DataSchema.class);
        Map<String, Object> fields = spec.input() != null ? new LinkedHashMap<>(spec.input()) : new LinkedHashMap<>();
        signBundleSmoke(deployed.sourceBody(), inputSchema, fields);
        DataRecord input = DataRecord.single(inputSchema, fields);
        DataRecord output = functionRuntime.invokeInCurrentTransaction(spec.objectPath(), spec.functionName(), input);
        Map<String, Object> row = output.rowCount() > 0 ? new LinkedHashMap<>(output.firstRow()) : Map.of();
        details.put("rowCount", output.rowCount());
        details.put("fields", row);
        assertExpectations(spec.expect(), output.rowCount(), row, errors);
    }

    private void signBundleSmoke(String sourceBody, DataSchema schema, Map<String, Object> fields) throws Exception {
        if (signatureVerifier == null || sourceBody == null || !sourceBody.contains("\"REQUIRED\"")) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return;
        }
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        signatureVerifier.enroll(
                authentication.getName(),
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())
        );
        if (!fields.containsKey("signature")) {
            fields.put("signature", "pending");
        }
        DataRecord unsigned = DataRecord.single(schema, fields);
        byte[] message = FunctionSignature.message("REQUIRED", sourceBody, authentication.getName(), "", unsigned);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(message);
        fields.put("signature", Base64.getEncoder().encodeToString(signer.sign()));
    }

    private void runTelemetry(TestSpec spec, Map<String, Object> details, List<String> errors) {
        if (isBlank(spec.objectPath()) || isBlank(spec.variable())) {
            throw new IllegalArgumentException("objectPath and variable are required");
        }
        platformScriptBridge.setDriverTelemetry(spec.objectPath(), spec.variable(), spec.fields());
        Map<String, Object> row = readVariable(spec.objectPath(), spec.variable());
        details.put("fields", row);
        assertExpectations(spec.expect(), 1, row, errors);
    }

    private void runFixtureSql(String appId, List<String> fixtureSql) {
        if (fixtureSql == null || fixtureSql.isEmpty()) {
            return;
        }
        String schemaName = dataStore.findApp(appId)
                .map(app -> String.valueOf(app.get("schema_name")))
                .filter(name -> name != null && !name.isBlank() && !"null".equals(name))
                .orElse(ApplicationSchemaSupport.defaultSchemaName(appId));
        schemaSession.runInSchema(schemaName, () -> {
            for (String sql : fixtureSql) {
                if (sql != null && !sql.isBlank()) {
                    jdbcTemplate.execute(sql);
                }
            }
        });
    }

    private Map<String, Object> readVariable(String objectPath, String variable) {
        return objectManager.require(objectPath)
                .getVariable(variable)
                .flatMap(v -> v.value().map(DataRecord::firstRow))
                .map(LinkedHashMap::new)
                .map(row -> (Map<String, Object>) row)
                .orElse(Map.of());
    }

    private static void assertExpectations(
            Map<String, Object> expect,
            int rowCount,
            Map<String, Object> row,
            List<String> errors
    ) {
        if (expect == null || expect.isEmpty()) {
            return;
        }
        if (expect.get("rowCount") instanceof Number expected && rowCount != expected.intValue()) {
            errors.add("rowCount expected " + expected.intValue() + " but was " + rowCount);
        }
        Object errorCode = expect.get("errorCode");
        if (errorCode != null) {
            String actual = ApplicationFunctionRuntime.errorCode(row);
            if (!Objects.equals(String.valueOf(errorCode), actual)) {
                errors.add("errorCode expected " + errorCode + " but was " + actual);
            }
        }
        Object fieldsRaw = expect.get("fields");
        if (fieldsRaw instanceof Map<?, ?> fields) {
            for (Map.Entry<?, ?> entry : fields.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object actual = row.get(key);
                if (!valuesEqual(entry.getValue(), actual)) {
                    errors.add("field " + key + " expected " + entry.getValue() + " but was " + actual);
                }
            }
        }
    }

    private static boolean valuesEqual(Object expected, Object actual) {
        if (expected instanceof Number expectedNumber && actual instanceof Number actualNumber) {
            return Double.compare(expectedNumber.doubleValue(), actualNumber.doubleValue()) == 0;
        }
        return Objects.equals(String.valueOf(expected), String.valueOf(actual));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record TestSpec(
            String id,
            String kind,
            String objectPath,
            String functionName,
            String variable,
            Map<String, Object> input,
            List<String> fixtureSql,
            Map<String, Object> fields,
            Map<String, Object> expect,
            boolean rollback
    ) {
        public TestSpec {
            id = isBlank(id) ? "adhoc" : id;
            kind = isBlank(kind) ? "function" : kind;
            fixtureSql = fixtureSql != null ? List.copyOf(fixtureSql) : List.of();
            fields = fields != null ? Map.copyOf(fields) : Map.of();
            input = input != null ? Map.copyOf(input) : Map.of();
            expect = expect != null ? Map.copyOf(expect) : Map.of();
        }
    }

    public record TestResult(
            String id,
            String kind,
            String status,
            List<String> errors,
            Map<String, Object> details
    ) {
    }
}
