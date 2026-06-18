package com.ispf.server.plugin.oilterminal;

/**
 * BPMN definitions for oil terminal reference stand.
 */
public final class OilTerminalWorkflowDefinitions {

    public static final String DISPATCH_TRUCK = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn"
                         targetNamespace="http://ispf.io/bpmn/oil">
              <process id="dispatch-truck" name="Dispatch Truck P-301" isExecutable="true">
                <startEvent id="start" name="Start"/>
                <userTask id="assignTask" name="Assign tank and rack"
                          ispf:title="Назначить РВС и эстакаду"
                          ispf:instructions="Выберите резервуар и эстакаду для наряда"
                          ispf:assigneeRole="admin"
                          ispf:targetObject="root.platform.oil-terminal.orders.dispatch4521"
                          ispf:function="assign"/>
                <userTask id="startTask" name="Start filling"
                          ispf:title="Начать налив"
                          ispf:instructions="Подтвердите старт налива на эстакаде"
                          ispf:assigneeRole="operator"
                          ispf:targetObject="root.platform.oil-terminal.orders.dispatch4521"
                          ispf:function="start"/>
                <userTask id="completeTask" name="Complete filling"
                          ispf:title="Завершить налив"
                          ispf:instructions="Подтвердите завершение налива"
                          ispf:assigneeRole="operator"
                          ispf:targetObject="root.platform.oil-terminal.orders.dispatch4521"
                          ispf:function="complete"/>
                <userTask id="closeTask" name="Close order"
                          ispf:title="Закрыть наряд"
                          ispf:instructions="Закрыть наряд после передачи факта в учёт"
                          ispf:assigneeRole="admin"
                          ispf:targetObject="root.platform.oil-terminal.orders.dispatch4521"
                          ispf:function="close"/>
                <endEvent id="end" name="End"/>
                <sequenceFlow id="f1" sourceRef="start" targetRef="assignTask"/>
                <sequenceFlow id="f2" sourceRef="assignTask" targetRef="startTask"/>
                <sequenceFlow id="f3" sourceRef="startTask" targetRef="completeTask"/>
                <sequenceFlow id="f4" sourceRef="completeTask" targetRef="closeTask"/>
                <sequenceFlow id="f5" sourceRef="closeTask" targetRef="end"/>
              </process>
            </definitions>
            """;

    public static final String LAB_APPROVAL = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn"
                         targetNamespace="http://ispf.io/bpmn/oil">
              <process id="lab-approval" name="Lab Approval P-210" isExecutable="true">
                <startEvent id="start" name="Start"/>
                <userTask id="approveTask" name="Approve sample"
                          ispf:title="Допуск партии"
                          ispf:instructions="Подтвердите результаты лаборатории"
                          ispf:assigneeRole="admin"
                          ispf:targetObject="root.platform.oil-terminal.samples.sample-rvs3-01"
                          ispf:function="approve"/>
                <endEvent id="end" name="End"/>
                <sequenceFlow id="f1" sourceRef="start" targetRef="approveTask"/>
                <sequenceFlow id="f2" sourceRef="approveTask" targetRef="end"/>
              </process>
            </definitions>
            """;

    private OilTerminalWorkflowDefinitions() {
    }
}
