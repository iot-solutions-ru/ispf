package com.ispf.server.api.dto;

import com.ispf.core.object.BlueprintContribution;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.Variable;
import com.ispf.server.federation.FederationProxyMetadata;
import com.ispf.server.object.BindingStateVariables;
import com.ispf.server.object.ObjectUiIconService;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.springframework.security.core.Authentication;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Universal object editor payload — all editable aspects of an object in one response.
 */
public record ObjectEditorDto(
        ObjectDto object,
        List<VariableDto> variables,
        List<EventDescriptor> events,
        List<FunctionDescriptor> functions,
        BlueprintOwnershipDto ownership
) {
    public static ObjectEditorDto from(PlatformObject node) {
        return from(node, null);
    }

    public static ObjectEditorDto from(PlatformObject node, ObjectUiIconService iconService) {
        return from(node, iconService, node.variables().values());
    }

    public static ObjectEditorDto from(
            PlatformObject node,
            ObjectUiIconService iconService,
            Authentication authentication,
            VariableMemberAccessService variableMemberAccessService
    ) {
        return from(
                node,
                iconService,
                variableMemberAccessService.filterReadable(
                        node.path(),
                        node.variables().values(),
                        authentication
                )
        );
    }

    private static ObjectEditorDto from(
            PlatformObject node,
            ObjectUiIconService iconService,
            Collection<Variable> readableVariables
    ) {
        String iconId = iconService != null
                ? iconService.readIconId(node).orElse(null)
                : null;
        List<VariableDto> variables = readableVariables.stream()
                .filter(v -> !ObjectUiIconService.UI_ICON_VARIABLE.equals(v.name()))
                .filter(v -> !BindingStateVariables.isReserved(v.name()))
                .filter(v -> !FederationProxyMetadata.isFederationVariable(v.name()))
                .map(VariableDto::from)
                .sorted((a, b) -> a.name().compareTo(b.name()))
                .toList();
        return new ObjectEditorDto(
                ObjectDto.from(node, iconId).withVariableNames(
                        variables.stream().map(VariableDto::name).toList()
                ),
                variables,
                node.events().values().stream().sorted(
                        (a, b) -> a.name().compareTo(b.name())
                ).toList(),
                node.functions().values().stream().sorted(
                        (a, b) -> a.name().compareTo(b.name())
                ).toList(),
                BlueprintOwnershipDto.from(node)
        );
    }

    public record BlueprintOwnershipDto(
            List<String> variables,
            List<String> events,
            List<String> functions,
            List<String> bindingRuleIds
    ) {
        public static BlueprintOwnershipDto from(PlatformObject node) {
            LinkedHashSet<String> variables = new LinkedHashSet<>();
            LinkedHashSet<String> events = new LinkedHashSet<>();
            LinkedHashSet<String> functions = new LinkedHashSet<>();
            LinkedHashSet<String> bindingRuleIds = new LinkedHashSet<>();
            for (BlueprintContribution contribution : node.blueprintContributions().values()) {
                variables.addAll(contribution.variables());
                events.addAll(contribution.events());
                functions.addAll(contribution.functions());
                bindingRuleIds.addAll(contribution.bindingRuleIds());
            }
            return new BlueprintOwnershipDto(
                    List.copyOf(variables),
                    List.copyOf(events),
                    List.copyOf(functions),
                    List.copyOf(bindingRuleIds)
            );
        }
    }
}
