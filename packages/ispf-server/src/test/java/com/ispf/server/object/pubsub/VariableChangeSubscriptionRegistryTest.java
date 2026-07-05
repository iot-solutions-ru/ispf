package com.ispf.server.object.pubsub;

import com.ispf.core.dashboard.DashboardContextConstants;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.alert.AlertRule;
import com.ispf.server.automation.AutomationRuleIndex;
import com.ispf.server.object.BindingDependencyIndex;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.workflow.WorkflowEventTriggerIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VariableChangeSubscriptionRegistryTest {

    private static final String PATH = "root.dev.sensor";
    private static final String VAR = "temperature";

    @Mock
    private ObjectManager objectManager;

    @Mock
    private BindingDependencyIndex bindingDependencyIndex;

    @Mock
    private AutomationRuleIndex automationRuleIndex;

    @Mock
    private WorkflowEventTriggerIndex workflowTriggerIndex;

    @Mock
    private ObjectWebSocketPathInterestRegistry webSocketPathInterest;

    @Mock
    private FederationExportInterestRegistry federationExportInterest;

    @Mock
    private ClusterPathInterestStore clusterPathInterest;

    private ObjectTree tree;
    private VariableChangeSubscriptionRegistry registry;

    @BeforeEach
    void setUp() {
        tree = new ObjectTree();
        PlatformObject sensor = new PlatformObject("sensor", PATH, ObjectType.DEVICE, "Sensor", "", null);
        tree.register(sensor);
        when(objectManager.tree()).thenReturn(tree);
        when(webSocketPathInterest.hasPathInterest(PATH)).thenReturn(false);
        when(federationExportInterest.hasPathInterest(PATH)).thenReturn(false);
        when(clusterPathInterest.hasPathInterest(PATH)).thenReturn(false);
        registry = new VariableChangeSubscriptionRegistry(
                objectManager,
                bindingDependencyIndex,
                automationRuleIndex,
                workflowTriggerIndex,
                webSocketPathInterest,
                federationExportInterest,
                clusterPathInterest
        );
    }

    @Test
    void returnsNoneForBlankPath() {
        assertThat(registry.interest("", VAR)).isEqualTo(VariableChangeInterest.NONE);
        assertThat(registry.interest(PATH, "")).isEqualTo(VariableChangeInterest.NONE);
    }

    @Test
    void detectsHistorianInterest() {
        DataSchema schema = DataSchema.builder(VAR).field("value", FieldType.DOUBLE).build();
        tree.findByPath(PATH).orElseThrow()
                .addVariable(new Variable(VAR, schema, true, false, null, true, null));

        VariableChangeInterest interest = registry.interest(PATH, VAR);

        assertThat(interest.historian()).isTrue();
        assertThat(interest.hasAny()).isTrue();
    }

    @Test
    void detectsClusterPathInterest() {
        when(clusterPathInterest.hasPathInterest(PATH)).thenReturn(true);

        VariableChangeInterest interest = registry.interest(PATH, VAR);

        assertThat(interest.uiRefresh()).isTrue();
        assertThat(interest.hasAny()).isTrue();
    }

    @Test
    void detectsWebSocketInterest() {
        when(webSocketPathInterest.hasPathInterest(PATH)).thenReturn(true);

        VariableChangeInterest interest = registry.interest(PATH, VAR);

        assertThat(interest.uiRefresh()).isTrue();
        assertThat(interest.hasAny()).isTrue();
    }

    @Test
    void detectsWorkflowIndexMaintenanceVariables() {
        String workflowPath = "root.platform.workflows.demo";
        VariableChangeInterest interest = registry.interest(workflowPath, "triggerJson");

        assertThat(interest.workflowIndex()).isTrue();
        assertThat(interest.automation()).isTrue();
    }

    @Test
    void detectsDashboardContextBindingInterest() {
        VariableChangeInterest interest = registry.interest(
                "root.platform.dashboards.demo-sensor",
                DashboardContextConstants.VARIABLE
        );

        assertThat(interest.bindings()).isTrue();
        assertThat(interest.automation()).isTrue();
        assertThat(interest.hasAny()).isTrue();
    }

    @Test
    void detectsBindingConsumers() {
        when(bindingDependencyIndex.consumers(PATH, VAR)).thenReturn(Set.of("root.other"));

        VariableChangeInterest interest = registry.interest(PATH, VAR);

        assertThat(interest.bindings()).isTrue();
        assertThat(interest.automation()).isTrue();
    }

    @Test
    void detectsAlertRules() {
        when(automationRuleIndex.findAlertRules(PATH, VAR)).thenReturn(List.of(sampleAlertRule()));

        VariableChangeInterest interest = registry.interest(PATH, VAR);

        assertThat(interest.alerts()).isTrue();
        assertThat(interest.automation()).isTrue();
    }

    @Test
    void detectsWorkflowTriggers() {
        when(workflowTriggerIndex.findVariableWorkflows(PATH, VAR)).thenReturn(List.of("wf-1"));

        VariableChangeInterest interest = registry.interest(PATH, VAR);

        assertThat(interest.workflows()).isTrue();
        assertThat(interest.automation()).isTrue();
    }

    private static AlertRule sampleAlertRule() {
        return new AlertRule(
                "rule-1",
                "Test",
                PATH,
                VAR,
                "true",
                "alarm",
                null,
                true,
                false,
                0,
                false,
                0,
                "medium",
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
