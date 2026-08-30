package com.ispf.server.security.acl;

import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.Variable;
import com.ispf.server.object.ObjectManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Service
public class VariableMemberAccessService {

    private final ObjectManager objectManager;
    private final ObjectAccessService objectAccessService;

    public VariableMemberAccessService(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService
    ) {
        this.objectManager = objectManager;
        this.objectAccessService = objectAccessService;
    }

    public void requireRead(String objectPath, String variableName, Authentication authentication) {
        requireRead(requireVariable(objectPath, variableName), objectPath, authentication);
    }

    public void requireWrite(String objectPath, String variableName, Authentication authentication) {
        requireWrite(requireVariable(objectPath, variableName), objectPath, authentication);
    }

    public boolean canRead(String objectPath, String variableName, Authentication authentication) {
        try {
            return objectManager.require(objectPath)
                    .getVariable(variableName)
                    .map(variable -> objectAccessService.canVariableRead(
                            objectPath,
                            variable.name(),
                            variable.readRoles(),
                            authentication
                    ))
                    .orElse(false);
        } catch (ObjectNotFoundException ignored) {
            return false;
        }
    }

    public void requireRead(Variable variable, String objectPath, Authentication authentication) {
        Objects.requireNonNull(variable, "variable");
        objectAccessService.requireVariableRead(
                objectPath,
                variable.name(),
                variable.readRoles(),
                authentication
        );
    }

    public void requireWrite(Variable variable, String objectPath, Authentication authentication) {
        Objects.requireNonNull(variable, "variable");
        objectAccessService.requireVariableWrite(
                objectPath,
                variable.name(),
                variable.writeRoles(),
                authentication
        );
    }

    public List<Variable> filterReadable(
            String objectPath,
            Collection<Variable> variables,
            Authentication authentication
    ) {
        Objects.requireNonNull(variables, "variables");
        return variables.stream()
                .filter(Objects::nonNull)
                .filter(variable -> objectAccessService.canVariableRead(
                        objectPath,
                        variable.name(),
                        variable.readRoles(),
                        authentication
                ))
                .toList();
    }

    public void requireReadAll(Authentication authentication, List<VariableRef> variables) {
        Objects.requireNonNull(variables, "variables");
        for (VariableRef variable : variables) {
            Objects.requireNonNull(variable, "variable");
            requireRead(variable.objectPath(), variable.variableName(), authentication);
        }
    }

    private Variable requireVariable(String objectPath, String variableName) {
        return objectManager.require(objectPath)
                .getVariable(variableName)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Variable: " + variableName
                ));
    }

    public record VariableRef(String objectPath, String variableName) {
    }
}
