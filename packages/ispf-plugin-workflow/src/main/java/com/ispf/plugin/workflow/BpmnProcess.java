package com.ispf.plugin.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record BpmnProcess(
        String id,
        String name,
        String startNodeId,
        Map<String, String> nodeTypes,
        Map<String, ServiceTaskDefinition> serviceTasks,
        Map<String, UserTaskDefinition> userTasks,
        Map<String, MessageTaskDefinition> messageTasks,
        Map<String, SignalCatchDefinition> signalCatchEvents,
        Map<String, MessageCatchDefinition> messageCatchEvents,
        Map<String, TimerCatchDefinition> timerCatchEvents,
        Map<String, BoundaryTimerDefinition> boundaryTimers,
        List<SequenceFlowDefinition> sequenceFlows
) {
    public String resolveNext(String nodeId, WorkflowConditionEvaluator evaluator) throws WorkflowException {
        List<SequenceFlowDefinition> outgoing = outgoingFrom(nodeId);
        if (outgoing.isEmpty()) {
            return null;
        }

        SequenceFlowDefinition defaultFlow = null;
        for (SequenceFlowDefinition flow : outgoing) {
            if (flow.defaultFlow()) {
                defaultFlow = flow;
                continue;
            }
            String condition = flow.conditionExpression();
            if (condition == null || condition.isBlank()) {
                return flow.targetRef();
            }
            if (evaluator.evaluate(condition)) {
                return flow.targetRef();
            }
        }
        if (defaultFlow != null) {
            return defaultFlow.targetRef();
        }
        if (outgoing.size() == 1) {
            return outgoing.getFirst().targetRef();
        }
        throw new WorkflowException("No matching outgoing flow from node: " + nodeId);
    }

    public List<SequenceFlowDefinition> outgoingFrom(String nodeId) {
        List<SequenceFlowDefinition> result = new ArrayList<>();
        for (SequenceFlowDefinition flow : sequenceFlows) {
            if (nodeId.equals(flow.sourceRef())) {
                result.add(flow);
            }
        }
        return result;
    }

    public List<SequenceFlowDefinition> incomingTo(String nodeId) {
        List<SequenceFlowDefinition> result = new ArrayList<>();
        for (SequenceFlowDefinition flow : sequenceFlows) {
            if (nodeId.equals(flow.targetRef())) {
                result.add(flow);
            }
        }
        return result;
    }

    public boolean isParallelGateway(String nodeId) {
        return "parallelGateway".equals(nodeTypes.get(nodeId));
    }

    public boolean isParallelFork(String nodeId) {
        return isParallelGateway(nodeId) && outgoingFrom(nodeId).size() > 1;
    }

    public boolean isParallelJoin(String nodeId) {
        return isParallelGateway(nodeId) && incomingTo(nodeId).size() > 1;
    }

    public boolean isEnd(String nodeId) {
        return "endEvent".equals(nodeTypes.get(nodeId));
    }

    public boolean isGateway(String nodeId) {
        return "exclusiveGateway".equals(nodeTypes.get(nodeId));
    }

    /** @deprecated use {@link #resolveNext(String, WorkflowConditionEvaluator)} */
    @Deprecated
    public String nextNode(String nodeId) {
        List<SequenceFlowDefinition> outgoing = outgoingFrom(nodeId);
        if (outgoing.isEmpty()) {
            return null;
        }
        return outgoing.getFirst().targetRef();
    }
}
