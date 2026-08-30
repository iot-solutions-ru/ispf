package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.LiveVariablePort;
import com.ispf.core.model.DataRecord;
import com.ispf.core.object.Variable;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.acl.VariableAclRequestContext;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
public class AnalyticsLiveVariablePortAdapter implements LiveVariablePort {

    private final ObjectManager objectManager;
    private final VariableMemberAccessService variableMemberAccessService;

    AnalyticsLiveVariablePortAdapter(
            ObjectManager objectManager,
            VariableMemberAccessService variableMemberAccessService
    ) {
        this.objectManager = objectManager;
        this.variableMemberAccessService = variableMemberAccessService;
    }

    @Override
    public Optional<Double> readNumeric(String objectPath, String variableName, String fieldName) {
        if (VariableAclRequestContext.isMemberEnforced()) {
            variableMemberAccessService.requireRead(
                    objectPath,
                    variableName,
                    VariableAclRequestContext.requireAuthentication()
            );
        }
        return objectManager.tree().findByPath(objectPath)
                .flatMap(node -> node.getVariable(variableName))
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get(fieldName != null ? fieldName : "value"))
                .flatMap(AnalyticsLiveVariablePortAdapter::toDouble);
    }

    @Override
    public void writeNumeric(String objectPath, String variableName, String fieldName, double value) {
        // Write-back to tree is handled by AnalyticsEngineService after evaluation (BL-204 prep).
    }

    static Optional<Double> toDouble(Object raw) {
        if (raw == null) {
            return Optional.empty();
        }
        if (raw instanceof Number number) {
            return Optional.of(number.doubleValue());
        }
        try {
            return Optional.of(Double.parseDouble(String.valueOf(raw)));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
