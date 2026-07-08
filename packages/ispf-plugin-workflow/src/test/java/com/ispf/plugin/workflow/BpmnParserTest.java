package com.ispf.plugin.workflow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BpmnParserTest {

    @Test
    void parsesSimpleProcess() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="demo" name="Demo" isExecutable="true">
                    <startEvent id="start"/>
                    <serviceTask id="task" name="Log" ispf:action="log" ispf:message="hello"/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="task"/>
                    <sequenceFlow sourceRef="task" targetRef="end"/>
                  </process>
                </definitions>
                """;

        BpmnProcess process = new BpmnParser().parse(xml);

        assertThat(process.id()).isEqualTo("demo");
        assertThat(process.startNodeId()).isEqualTo("start");
        assertThat(process.nextNode("start")).isEqualTo("task");
        assertThat(process.serviceTasks().get("task").action()).isEqualTo(WorkflowActionType.LOG);
    }

    @Test
    void parsesMessageCatchEvent() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="msg" name="Message" isExecutable="true">
                    <startEvent id="start"/>
                    <intermediateCatchEvent id="waitMsg" name="Wait ERP ack">
                      <messageEventDefinition messageRef="erpAck"/>
                    </intermediateCatchEvent>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="waitMsg"/>
                    <sequenceFlow sourceRef="waitMsg" targetRef="end"/>
                  </process>
                </definitions>
                """;

        BpmnProcess process = new BpmnParser().parse(xml);

        assertThat(process.messageCatchEvents()).containsKey("waitMsg");
        assertThat(process.messageCatchEvents().get("waitMsg").messageName()).isEqualTo("erpAck");
    }

    @Test
    void parsesMessageThrowEvent() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="msg-throw" name="Message Throw" isExecutable="true">
                    <startEvent id="start"/>
                    <intermediateThrowEvent id="shipMsg" name="Ship order">
                      <messageEventDefinition messageRef="orderShipped"/>
                    </intermediateThrowEvent>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="shipMsg"/>
                    <sequenceFlow sourceRef="shipMsg" targetRef="end"/>
                  </process>
                </definitions>
                """;

        BpmnProcess process = new BpmnParser().parse(xml);

        assertThat(process.messageThrowEvents()).containsKey("shipMsg");
        assertThat(process.messageThrowEvents().get("shipMsg").messageName()).isEqualTo("orderShipped");
    }
}
