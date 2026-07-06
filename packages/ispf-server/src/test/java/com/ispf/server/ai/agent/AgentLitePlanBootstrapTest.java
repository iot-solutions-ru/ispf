package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLitePlanBootstrapTest {

    static Stream<String> snmpPrompts() {
        return Stream.of(
                "Создай демо SNMP-устройство с метриками и дашбордом мониторинга",
                "Подключи SNMP localhost и создай dashboard мониторинга"
        );
    }

    @ParameterizedTest
    @MethodSource("snmpPrompts")
    void resolvesSnmpMonitoringPlan(String prompt) {
        var plan = AgentLitePlanBootstrap.resolveDraftPlan(prompt, AgentPlanDepth.LITE);
        assertThat(plan).isPresent();
        assertThat(plan.get().get("goal")).asString().containsIgnoringCase("SNMP");
        @SuppressWarnings("unchecked")
        List<String> steps = (List<String>) plan.get().get("steps");
        assertThat(steps).hasSizeGreaterThanOrEqualTo(5);
        assertThat(steps.toString()).contains("snmp-host-monitoring");
    }

    @Test
    void resolvesWorkflowPlan() {
        var plan = AgentLitePlanBootstrap.resolveDraftPlan(
                "Создай workflow для симуляции гидроудара",
                AgentPlanDepth.LITE
        );
        assertThat(plan).isPresent();
        @SuppressWarnings("unchecked")
        List<String> steps = (List<String>) plan.get().get("steps");
        assertThat(steps.toString()).contains("save_workflow_bpmn");
    }

    @Test
    void resolvesMesBundlePlan() {
        var plan = AgentLitePlanBootstrap.resolveDraftPlan(
                "Разверни MES demo mes-reference и покажи orders",
                AgentPlanDepth.LITE
        );
        assertThat(plan).isPresent();
        @SuppressWarnings("unchecked")
        List<String> steps = (List<String>) plan.get().get("steps");
        assertThat(steps.toString()).contains("validate_bundle");
        assertThat(steps.toString()).contains("mes-reference");
    }

    @Test
    void resolvesPumpStationScadaPlan() {
        var plan = AgentLitePlanBootstrap.resolveDraftPlan(
                "Создай SCADA мнемосхему насосной станции с дашбордом",
                AgentPlanDepth.LITE
        );
        assertThat(plan).isPresent();
        @SuppressWarnings("unchecked")
        List<String> steps = (List<String>) plan.get().get("steps");
        assertThat(steps.toString()).contains("save_mimic_diagram");
    }

    @Test
    void resolvesAlertAutomationPlan() {
        var plan = AgentLitePlanBootstrap.resolveDraftPlan(
                "Настрой alert rule для high pressure на lab-pump-01",
                AgentPlanDepth.LITE
        );
        assertThat(plan).isPresent();
        @SuppressWarnings("unchecked")
        List<String> steps = (List<String>) plan.get().get("steps");
        assertThat(steps.toString()).contains("configure_alert");
    }

    @Test
    void skipsFullTzIndustrialFacility() {
        var plan = AgentLitePlanBootstrap.resolveDraftPlan(
                """
                ТЕХНИЧЕСКОЕ ЗАДАНИЕ на создание системы цифрового двойника насосной станции.
                Приложение B: перечень объектов — ЗД-01, НМ-1, СИКН-01, РВС-01.
                Функциональные требования: FR-1 realtime telemetry, FR-2 SCADA HMI L2.
                """,
                AgentPlanDepth.FULL
        );
        assertThat(plan).isEmpty();
    }

    @Test
    void seedsDraftOnBeginTurnForMutationTask() {
        AgentRunState state = new AgentRunState();
        state.setInteractionMode(AgentInteractionMode.AUTO);
        AgentPlanGuard.beginTurn(
                state,
                "Создай workflow для симуляции гидроудара",
                AgentProfile.ADMIN,
                true,
                null
        );
        assertThat(state.planPhase()).isEqualTo(AgentPlanPhase.PLANNING);
        assertThat(state.storedPlan()).isNotEmpty();
        assertThat(state.storedPlan().get("goal")).isNotNull();
    }

    @Test
    void recoversFinishPlanAfterRepeatedPlanningGuards() {
        List<Map<String, Object>> steps = List.of(
                Map.of("type", "guard", "error", AgentLitePlanBootstrap.PLANNING_GUARD_ERROR),
                Map.of("type", "guard", "error", AgentLitePlanBootstrap.PLANNING_GUARD_ERROR)
        );
        assertThat(AgentLitePlanBootstrap.shouldRecoverFromPlanningGuardLoop(
                steps,
                AgentLitePlanBootstrap.PLANNING_GUARD_ERROR
        )).isTrue();

        var finish = AgentLitePlanBootstrap.resolveFinishPlan(
                "Настрой alert rule для high pressure на lab-pump-01",
                new AgentRunState()
        );
        assertThat(finish).isPresent();
        assertThat(AgentPlanGuard.isPlanFinish(finish.get())).isTrue();
        assertThat(AgentPlanGuard.hasStructuredPlanContent(finish.get(), new AgentRunState())).isTrue();
    }

    @Test
    void referenceScenarioCatalogMatchesSnmpVariants() {
        assertThat(ReferenceScenarioCatalog.matchBest(
                "Создай демо SNMP-устройство с метриками и дашбордом мониторинга"
        )).isPresent();
        assertThat(ReferenceScenarioCatalog.matchBest(
                "Создай workflow для симуляции гидроудара"
        )).map(ReferenceScenarioCatalog.ReferenceScenario::id)
                .contains("workflow-hydro-impact");
    }
}
