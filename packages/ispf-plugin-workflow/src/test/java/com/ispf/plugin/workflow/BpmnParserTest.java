package com.ispf.plugin.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void rejectsNonMessageThrowEvent() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <process id="signal-throw" name="Signal Throw" isExecutable="true">
                    <startEvent id="start"/>
                    <intermediateThrowEvent id="throwSignal" name="Throw signal">
                      <signalEventDefinition signalRef="go"/>
                    </intermediateThrowEvent>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="throwSignal"/>
                    <sequenceFlow sourceRef="throwSignal" targetRef="end"/>
                  </process>
                </definitions>
                """;

        assertThatThrownBy(() -> new BpmnParser().parse(xml))
                .isInstanceOf(WorkflowException.class)
                .hasMessageContaining("Unsupported throw event (message only)")
                .hasMessageContaining("throwSignal");
    }

    @ParameterizedTest(name = "rejects {0}")
    @CsvSource({
            "callActivity, callActivity is not supported",
            "businessRuleTask, businessRuleTask / DMN is not supported",
            "inclusiveGateway, inclusiveGateway is not supported",
            "eventBasedGateway, eventBasedGateway is not supported"
    })
    void rejectsUnsupportedTopLevelElements(String elementName, String expectedMessageFragment) {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <process id="reject-demo" name="Reject" isExecutable="true">
                    <startEvent id="start"/>
                    <%s id="bad" name="Bad"/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="bad"/>
                    <sequenceFlow sourceRef="bad" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(elementName);

        assertThatThrownBy(() -> new BpmnParser().parse(xml))
                .isInstanceOf(WorkflowException.class)
                .hasMessageContaining(expectedMessageFragment)
                .hasMessageContaining("bad");
    }

    @Test
    void rejectsMultiInstance() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="mi" name="Multi" isExecutable="true">
                    <startEvent id="start"/>
                    <serviceTask id="task" name="Log" ispf:action="log" ispf:message="x">
                      <multiInstanceLoopCharacteristics isSequential="true"/>
                    </serviceTask>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="task"/>
                    <sequenceFlow sourceRef="task" targetRef="end"/>
                  </process>
                </definitions>
                """;

        assertThatThrownBy(() -> new BpmnParser().parse(xml))
                .isInstanceOf(WorkflowException.class)
                .hasMessageContaining("multi-instance is not supported");
    }

    @Test
    void rejectsCompensation() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <process id="comp" name="Comp" isExecutable="true">
                    <startEvent id="start"/>
                    <serviceTask id="work" name="Work"/>
                    <boundaryEvent id="compensate" attachedToRef="work">
                      <compensateEventDefinition/>
                    </boundaryEvent>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="work"/>
                    <sequenceFlow sourceRef="work" targetRef="end"/>
                  </process>
                </definitions>
                """;

        assertThatThrownBy(() -> new BpmnParser().parse(xml))
                .isInstanceOf(WorkflowException.class)
                .hasMessageContaining("compensation / compensate events are not supported");
    }

    @Test
    void rejectsEventSubprocess() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <process id="esp" name="Event sub" isExecutable="true">
                    <startEvent id="start"/>
                    <subProcess id="onMsg" name="On message" triggeredByEvent="true">
                      <startEvent id="msgStart"/>
                      <endEvent id="msgEnd"/>
                      <sequenceFlow sourceRef="msgStart" targetRef="msgEnd"/>
                    </subProcess>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="end"/>
                  </process>
                </definitions>
                """;

        assertThatThrownBy(() -> new BpmnParser().parse(xml))
                .isInstanceOf(WorkflowException.class)
                .hasMessageContaining("Event subprocess is not supported")
                .hasMessageContaining("onMsg");
    }
}
