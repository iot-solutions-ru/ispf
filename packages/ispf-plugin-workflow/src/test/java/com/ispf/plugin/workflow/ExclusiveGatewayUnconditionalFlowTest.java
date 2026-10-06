package com.ispf.plugin.workflow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExclusiveGatewayUnconditionalFlowTest {

    @Test
    void conditionalFlowWinsWhenListedAfterAnUnconditionalFlow() throws Exception {
        BpmnProcess process = new WorkflowEngine().parse(xorXml());

        String next = process.resolveNext("xor", expression -> "true".equals(expression));

        assertThat(next).isEqualTo("cond");
    }

    @Test
    void unconditionalFlowIsTheDefaultAfterConditionsFail() throws Exception {
        BpmnProcess process = new WorkflowEngine().parse(xorXml());

        String next = process.resolveNext("xor", expression -> false);

        assertThat(next).isEqualTo("uncond");
    }

    private static String xorXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="xor" isExecutable="true">
                    <startEvent id="start"/>
                    <exclusiveGateway id="xor"/>
                    <serviceTask id="uncond" name="Uncond" ispf:action="log" ispf:message="XOR_UNCOND"/>
                    <serviceTask id="cond" name="Cond" ispf:action="log" ispf:message="XOR_COND"/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="xor"/>
                    <sequenceFlow sourceRef="xor" targetRef="uncond"/>
                    <sequenceFlow sourceRef="xor" targetRef="cond" ispf:condition="true"/>
                    <sequenceFlow sourceRef="uncond" targetRef="end"/>
                    <sequenceFlow sourceRef="cond" targetRef="end"/>
                  </process>
                </definitions>
                """;
    }
}
