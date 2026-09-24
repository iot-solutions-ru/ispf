package com.ispf.server.workflow;

/**
 * Built-in BPMN definitions for demo workflows.
 */
public final class WorkflowDefinitions {

    public static final String DEMO_ALARM_HANDLER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn"
                         targetNamespace="http://ispf.io/bpmn">
              <process id="demo-alarm-handler" name="Demo Alarm Handler" isExecutable="true">
                <startEvent id="start" name="Start"/>
                <serviceTask id="recordAlarm" name="Record alarm"
                             ispf:action="setVariable"
                             ispf:targetObject="root.platform.workflows.demo-alarm-handler"
                             ispf:variable="lastAction"
                             ispf:value="alarm-detected"/>
                <parallelGateway id="fork" name="Parallel notify"/>
                <serviceTask id="auditLog" name="Audit log"
                             ispf:action="log"
                             ispf:message="audit: alarm recorded"/>
                <messageTask id="opsMessage" name="Notify operations"
                             ispf:subject="ispf.ops.alarm"
                             ispf:message="Threshold exceeded on demo sensor"
                             ispf:channel="nats"/>
                <parallelGateway id="join" name="Join"/>
                <exclusiveGateway id="gateway" name="Needs operator?"/>
                <userTask id="operatorAck" name="Operator acknowledgment"
                          ispf:title="Подтвердите тревогу"
                          ispf:instructions="Проверьте показания датчика и подтвердите алarm"
                          ispf:assigneeRole="operator"
                          ispf:targetObject="root.platform.devices.demo-sensor-01"
                          ispf:function="acknowledgeAlarm"/>
                <serviceTask id="notify" name="Publish NATS event"
                             ispf:action="publishNats"
                             ispf:subject="ispf.workflow.alarm.handled"
                             ispf:message="demo alarm handled"/>
                <endEvent id="end" name="End"/>
                <sequenceFlow id="f1" sourceRef="start" targetRef="recordAlarm"/>
                <sequenceFlow id="f2" sourceRef="recordAlarm" targetRef="fork"/>
                <sequenceFlow id="f2a" sourceRef="fork" targetRef="auditLog"/>
                <sequenceFlow id="f2b" sourceRef="fork" targetRef="opsMessage"/>
                <sequenceFlow id="f2c" sourceRef="auditLog" targetRef="join"/>
                <sequenceFlow id="f2d" sourceRef="opsMessage" targetRef="join"/>
                <sequenceFlow id="f3" sourceRef="join" targetRef="gateway"/>
                <sequenceFlow id="f4" sourceRef="gateway" targetRef="operatorAck"
                              ispf:condition="self.alarmAcknowledged[&quot;value&quot;] == false"/>
                <sequenceFlow id="f5" sourceRef="gateway" targetRef="notify" ispf:default="true"/>
                <sequenceFlow id="f6" sourceRef="operatorAck" targetRef="notify"/>
                <sequenceFlow id="f7" sourceRef="notify" targetRef="end"/>
              </process>
            </definitions>
            """;

    public static final String DEMO_ALARM_TRIGGER = """
            {}
            """;

    private WorkflowDefinitions() {
    }
}
