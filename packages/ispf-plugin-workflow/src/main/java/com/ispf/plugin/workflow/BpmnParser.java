package com.ispf.plugin.workflow;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * BPMN subset parser for ISPF workflows (ADR-0047). Supported: start/end, service/user/message
 * tasks, exclusive/parallel gateways, embedded subProcess, intermediate catch (signal/timer/message),
 * message throw, boundary timer, sequenceFlow with conditions. Unsupported elements fail at parse.
 */
public class BpmnParser {

    private static final String ISPF_NS = "http://ispf.io/bpmn";

    public BpmnProcess parse(String bpmnXml) throws WorkflowException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));

            Element process = firstElement(document, "process");
            if (process == null) {
                throw new WorkflowException("BPMN process element not found");
            }

            rejectUnsupportedElements(process);

            String processId = process.getAttribute("id");
            String processName = process.getAttribute("name");

            Map<String, String> nodeTypes = new HashMap<>();
            Map<String, ServiceTaskDefinition> serviceTasks = new HashMap<>();
            Map<String, UserTaskDefinition> userTasks = new HashMap<>();
            Map<String, MessageTaskDefinition> messageTasks = new HashMap<>();
            Map<String, SignalCatchDefinition> signalCatchEvents = new HashMap<>();
            Map<String, MessageCatchDefinition> messageCatchEvents = new HashMap<>();
            Map<String, MessageThrowDefinition> messageThrowEvents = new HashMap<>();
            Map<String, TimerCatchDefinition> timerCatchEvents = new HashMap<>();
            Map<String, BoundaryTimerDefinition> boundaryTimers = new HashMap<>();
            Map<String, SubProcessDefinition> subProcesses = new HashMap<>();
            Map<String, String> endEventToSubProcess = new HashMap<>();
            List<SequenceFlowDefinition> sequenceFlows = new ArrayList<>();
            String startNodeId = null;

            for (Element start : elements(process, "startEvent")) {
                if (startNodeId == null) {
                    startNodeId = start.getAttribute("id");
                    nodeTypes.put(startNodeId, "startEvent");
                }
            }

            for (Element end : elements(process, "endEvent")) {
                String id = end.getAttribute("id");
                nodeTypes.put(id, "endEvent");
            }

            for (Element gateway : elements(process, "exclusiveGateway")) {
                String id = gateway.getAttribute("id");
                nodeTypes.put(id, "exclusiveGateway");
            }

            for (Element gateway : elements(process, "parallelGateway")) {
                String id = gateway.getAttribute("id");
                nodeTypes.put(id, "parallelGateway");
            }

            for (Element task : elements(process, "serviceTask")) {
                String id = task.getAttribute("id");
                nodeTypes.put(id, "serviceTask");
                serviceTasks.put(id, parseServiceTask(task));
            }

            for (Element task : elements(process, "userTask")) {
                String id = task.getAttribute("id");
                nodeTypes.put(id, "userTask");
                userTasks.put(id, parseUserTask(task));
            }

            for (Element task : elements(process, "messageTask")) {
                String id = task.getAttribute("id");
                nodeTypes.put(id, "messageTask");
                messageTasks.put(id, parseMessageTask(task));
            }

            for (Element catchEvent : elements(process, "intermediateCatchEvent")) {
                String id = catchEvent.getAttribute("id");
                nodeTypes.put(id, "intermediateCatchEvent");
                Integer durationSeconds = readDurationSeconds(catchEvent);
                if (durationSeconds != null) {
                    Map<String, String> parameters = new HashMap<>();
                    parameters.put("durationSeconds", String.valueOf(durationSeconds));
                    timerCatchEvents.put(id, new TimerCatchDefinition(
                            id,
                            catchEvent.getAttribute("name"),
                            durationSeconds,
                            Map.copyOf(parameters)
                    ));
                } else if (firstChildElement(catchEvent, "messageEventDefinition") != null) {
                    messageCatchEvents.put(id, parseMessageCatch(catchEvent));
                } else {
                    signalCatchEvents.put(id, parseSignalCatch(catchEvent));
                }
            }

            for (Element throwEvent : elements(process, "intermediateThrowEvent")) {
                String id = throwEvent.getAttribute("id");
                nodeTypes.put(id, "intermediateThrowEvent");
                if (firstChildElement(throwEvent, "messageEventDefinition") != null) {
                    messageThrowEvents.put(id, parseMessageThrow(throwEvent));
                } else {
                    throw new WorkflowException("Unsupported throw event (message only): " + id);
                }
            }

            for (Element boundary : elements(process, "boundaryEvent")) {
                BoundaryTimerDefinition boundaryTimer = parseBoundaryTimer(boundary);
                if (boundaryTimer != null) {
                    boundaryTimers.put(boundaryTimer.attachedToRef(), boundaryTimer);
                }
            }

            for (Element subProcess : elements(process, "subProcess")) {
                parseSubProcess(
                        subProcess,
                        nodeTypes,
                        serviceTasks,
                        userTasks,
                        messageTasks,
                        signalCatchEvents,
                        messageCatchEvents,
                        messageThrowEvents,
                        timerCatchEvents,
                        boundaryTimers,
                        subProcesses,
                        endEventToSubProcess,
                        sequenceFlows
                );
            }

            for (Element flow : elements(process, "sequenceFlow")) {
                sequenceFlows.add(parseSequenceFlow(flow));
            }

            if (startNodeId == null || startNodeId.isBlank()) {
                throw new WorkflowException("BPMN startEvent is required");
            }

            return new BpmnProcess(
                    processId,
                    processName,
                    startNodeId,
                    nodeTypes,
                    serviceTasks,
                    userTasks,
                    messageTasks,
                    signalCatchEvents,
                    messageCatchEvents,
                    messageThrowEvents,
                    timerCatchEvents,
                    boundaryTimers,
                    subProcesses,
                    endEventToSubProcess,
                    List.copyOf(sequenceFlows)
            );
        } catch (WorkflowException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowException("Failed to parse BPMN XML", e);
        }
    }

    private void parseSubProcess(
            Element subProcess,
            Map<String, String> nodeTypes,
            Map<String, ServiceTaskDefinition> serviceTasks,
            Map<String, UserTaskDefinition> userTasks,
            Map<String, MessageTaskDefinition> messageTasks,
            Map<String, SignalCatchDefinition> signalCatchEvents,
            Map<String, MessageCatchDefinition> messageCatchEvents,
            Map<String, MessageThrowDefinition> messageThrowEvents,
            Map<String, TimerCatchDefinition> timerCatchEvents,
            Map<String, BoundaryTimerDefinition> boundaryTimers,
            Map<String, SubProcessDefinition> subProcesses,
            Map<String, String> endEventToSubProcess,
            List<SequenceFlowDefinition> sequenceFlows
    ) throws WorkflowException {
        String id = subProcess.getAttribute("id");
        nodeTypes.put(id, "subProcess");

        String subStartNodeId = null;
        List<String> subEndNodeIds = new ArrayList<>();

        for (Element start : elements(subProcess, "startEvent")) {
            String startId = start.getAttribute("id");
            if (subStartNodeId == null) {
                subStartNodeId = startId;
            }
            nodeTypes.put(startId, "startEvent");
        }

        for (Element end : elements(subProcess, "endEvent")) {
            String endId = end.getAttribute("id");
            nodeTypes.put(endId, "endEvent");
            subEndNodeIds.add(endId);
            endEventToSubProcess.put(endId, id);
        }

        for (Element gateway : elements(subProcess, "exclusiveGateway")) {
            nodeTypes.put(gateway.getAttribute("id"), "exclusiveGateway");
        }

        for (Element gateway : elements(subProcess, "parallelGateway")) {
            nodeTypes.put(gateway.getAttribute("id"), "parallelGateway");
        }

        for (Element task : elements(subProcess, "serviceTask")) {
            String taskId = task.getAttribute("id");
            nodeTypes.put(taskId, "serviceTask");
            serviceTasks.put(taskId, parseServiceTask(task));
        }

        for (Element task : elements(subProcess, "userTask")) {
            String taskId = task.getAttribute("id");
            nodeTypes.put(taskId, "userTask");
            userTasks.put(taskId, parseUserTask(task));
        }

        for (Element task : elements(subProcess, "messageTask")) {
            String taskId = task.getAttribute("id");
            nodeTypes.put(taskId, "messageTask");
            messageTasks.put(taskId, parseMessageTask(task));
        }

        for (Element catchEvent : elements(subProcess, "intermediateCatchEvent")) {
            String catchId = catchEvent.getAttribute("id");
            nodeTypes.put(catchId, "intermediateCatchEvent");
            Integer durationSeconds = readDurationSeconds(catchEvent);
            if (durationSeconds != null) {
                Map<String, String> parameters = new HashMap<>();
                parameters.put("durationSeconds", String.valueOf(durationSeconds));
                timerCatchEvents.put(catchId, new TimerCatchDefinition(
                        catchId,
                        catchEvent.getAttribute("name"),
                        durationSeconds,
                        Map.copyOf(parameters)
                ));
            } else if (firstChildElement(catchEvent, "messageEventDefinition") != null) {
                messageCatchEvents.put(catchId, parseMessageCatch(catchEvent));
            } else {
                signalCatchEvents.put(catchId, parseSignalCatch(catchEvent));
            }
        }

        for (Element throwEvent : elements(subProcess, "intermediateThrowEvent")) {
            String throwId = throwEvent.getAttribute("id");
            nodeTypes.put(throwId, "intermediateThrowEvent");
            if (firstChildElement(throwEvent, "messageEventDefinition") != null) {
                messageThrowEvents.put(throwId, parseMessageThrow(throwEvent));
            } else {
                throw new WorkflowException("Unsupported throw event (message only): " + throwId);
            }
        }

        for (Element boundary : elements(subProcess, "boundaryEvent")) {
            BoundaryTimerDefinition boundaryTimer = parseBoundaryTimer(boundary);
            if (boundaryTimer != null) {
                boundaryTimers.put(boundaryTimer.attachedToRef(), boundaryTimer);
            }
        }

        for (Element nested : elements(subProcess, "subProcess")) {
            parseSubProcess(
                    nested,
                    nodeTypes,
                    serviceTasks,
                    userTasks,
                    messageTasks,
                    signalCatchEvents,
                    messageCatchEvents,
                    messageThrowEvents,
                    timerCatchEvents,
                    boundaryTimers,
                    subProcesses,
                    endEventToSubProcess,
                    sequenceFlows
            );
        }

        for (Element flow : elements(subProcess, "sequenceFlow")) {
            sequenceFlows.add(parseSequenceFlow(flow));
        }

        if (subStartNodeId == null || subStartNodeId.isBlank()) {
            throw new WorkflowException("BPMN subprocess startEvent is required: " + id);
        }

        subProcesses.put(
                id,
                new SubProcessDefinition(id, subProcess.getAttribute("name"), subStartNodeId, List.copyOf(subEndNodeIds))
        );
    }

    /**
     * ADR-0047 freeze: reject elements outside the ISPF BPMN subset with a clear error.
     */
    private static void rejectUnsupportedElements(Element process) throws WorkflowException {
        rejectNamedElements(process, "callActivity",
                "callActivity is not supported (ISPF BPMN subset)");
        rejectNamedElements(process, "businessRuleTask",
                "businessRuleTask / DMN is not supported (ISPF BPMN subset)");
        rejectNamedElements(process, "inclusiveGateway",
                "inclusiveGateway is not supported (ISPF BPMN subset)");
        rejectNamedElements(process, "eventBasedGateway",
                "eventBasedGateway is not supported (ISPF BPMN subset)");
        rejectNamedElements(process, "multiInstanceLoopCharacteristics",
                "multi-instance is not supported (ISPF BPMN subset)");
        rejectNamedElements(process, "compensateEventDefinition",
                "compensation / compensate events are not supported (ISPF BPMN subset)");

        NodeList subProcesses = process.getElementsByTagNameNS("*", "subProcess");
        for (int i = 0; i < subProcesses.getLength(); i++) {
            Element sub = (Element) subProcesses.item(i);
            if ("true".equalsIgnoreCase(sub.getAttribute("triggeredByEvent"))) {
                String id = sub.getAttribute("id");
                throw new WorkflowException(
                        "Event subprocess is not supported (ISPF BPMN subset): "
                                + (id == null || id.isBlank() ? "<unknown>" : id));
            }
        }
    }

    private static void rejectNamedElements(Element scope, String localName, String message)
            throws WorkflowException {
        NodeList nodes = scope.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() > 0) {
            Element first = (Element) nodes.item(0);
            String id = first.getAttribute("id");
            if (id != null && !id.isBlank()) {
                throw new WorkflowException(message + ": " + id);
            }
            throw new WorkflowException(message);
        }
    }

    private SequenceFlowDefinition parseSequenceFlow(Element flow) {
        String id = flow.getAttribute("id");
        String source = flow.getAttribute("sourceRef");
        String target = flow.getAttribute("targetRef");
        String condition = readIspfAttribute(flow, "condition");
        if (condition == null || condition.isBlank()) {
            condition = readIspfAttribute(flow, "conditionExpression");
        }
        boolean defaultFlow = "true".equalsIgnoreCase(readIspfAttribute(flow, "default"));
        return new SequenceFlowDefinition(id, source, target, condition, defaultFlow);
    }

    private BoundaryTimerDefinition parseBoundaryTimer(Element boundary) throws WorkflowException {
        Integer durationSeconds = readDurationSeconds(boundary);
        if (durationSeconds == null) {
            return null;
        }
        String attachedToRef = boundary.getAttribute("attachedToRef");
        if (attachedToRef == null || attachedToRef.isBlank()) {
            throw new WorkflowException("Boundary timer requires attachedToRef: " + boundary.getAttribute("id"));
        }
        boolean interrupting = !"false".equalsIgnoreCase(boundary.getAttribute("cancelActivity"));
        Map<String, String> parameters = new HashMap<>();
        parameters.put("durationSeconds", String.valueOf(durationSeconds));
        parameters.put("attachedToRef", attachedToRef);
        return new BoundaryTimerDefinition(
                boundary.getAttribute("id"),
                boundary.getAttribute("name"),
                attachedToRef,
                durationSeconds,
                interrupting,
                Map.copyOf(parameters)
        );
    }

    private static Integer readDurationSeconds(Element element) {
        String raw = readIspfAttribute(element, "durationSeconds");
        if (raw == null || raw.isBlank()) {
            Element timerDef = firstChildElement(element, "timerEventDefinition");
            if (timerDef != null) {
                raw = readIspfAttribute(timerDef, "durationSeconds");
            }
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int seconds = Integer.parseInt(raw.trim());
            return seconds >= 0 ? seconds : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private MessageThrowDefinition parseMessageThrow(Element throwEvent) throws WorkflowException {
        Map<String, String> parameters = new HashMap<>();
        readIspfAttribute(throwEvent, parameters, "message");
        String messageName = parameters.get("message");
        if (messageName == null || messageName.isBlank()) {
            Element messageDef = firstChildElement(throwEvent, "messageEventDefinition");
            if (messageDef != null) {
                messageName = messageDef.getAttribute("messageRef");
                if (messageName == null || messageName.isBlank()) {
                    messageName = readIspfAttribute(messageDef, "message");
                }
            }
        }
        if (messageName == null || messageName.isBlank()) {
            throw new WorkflowException("Message throw event requires ispf:message or messageRef: "
                    + throwEvent.getAttribute("id"));
        }
        return new MessageThrowDefinition(
                throwEvent.getAttribute("id"),
                throwEvent.getAttribute("name"),
                messageName,
                Map.copyOf(parameters)
        );
    }

    private MessageCatchDefinition parseMessageCatch(Element catchEvent) throws WorkflowException {
        Map<String, String> parameters = new HashMap<>();
        readIspfAttribute(catchEvent, parameters, "message");
        String messageName = parameters.get("message");
        if (messageName == null || messageName.isBlank()) {
            Element messageDef = firstChildElement(catchEvent, "messageEventDefinition");
            if (messageDef != null) {
                messageName = messageDef.getAttribute("messageRef");
                if (messageName == null || messageName.isBlank()) {
                    messageName = readIspfAttribute(messageDef, "message");
                }
            }
        }
        if (messageName == null || messageName.isBlank()) {
            throw new WorkflowException("Message catch event requires ispf:message or messageRef: "
                    + catchEvent.getAttribute("id"));
        }
        return new MessageCatchDefinition(
                catchEvent.getAttribute("id"),
                catchEvent.getAttribute("name"),
                messageName,
                Map.copyOf(parameters)
        );
    }

    private SignalCatchDefinition parseSignalCatch(Element catchEvent) throws WorkflowException {
        Map<String, String> parameters = new HashMap<>();
        readIspfAttribute(catchEvent, parameters, "signal");
        String signalName = parameters.get("signal");
        if (signalName == null || signalName.isBlank()) {
            Element signalDef = firstChildElement(catchEvent, "signalEventDefinition");
            if (signalDef != null) {
                signalName = signalDef.getAttribute("signalRef");
                if (signalName == null || signalName.isBlank()) {
                    signalName = readIspfAttribute(signalDef, "signal");
                }
            }
        }
        if (signalName == null || signalName.isBlank()) {
            throw new WorkflowException("Signal catch event requires ispf:signal: " + catchEvent.getAttribute("id"));
        }
        return new SignalCatchDefinition(
                catchEvent.getAttribute("id"),
                catchEvent.getAttribute("name"),
                signalName,
                Map.copyOf(parameters)
        );
    }

    private MessageTaskDefinition parseMessageTask(Element task) {
        Map<String, String> parameters = new HashMap<>();
        readIspfAttribute(task, parameters, "subject");
        readIspfAttribute(task, parameters, "message");
        readIspfAttribute(task, parameters, "channel");
        String subject = parameters.getOrDefault("subject", "ispf.workflow.message");
        String message = parameters.getOrDefault("message", task.getAttribute("name"));
        String channel = parameters.getOrDefault("channel", "nats");
        return new MessageTaskDefinition(
                task.getAttribute("id"),
                task.getAttribute("name"),
                subject,
                message,
                channel,
                Map.copyOf(parameters)
        );
    }

    private UserTaskDefinition parseUserTask(Element task) {
        Map<String, String> parameters = new HashMap<>();
        readIspfAttribute(task, parameters, "assigneeRole");
        readIspfAttribute(task, parameters, "title");
        readIspfAttribute(task, parameters, "instructions");
        readIspfAttribute(task, parameters, "action");
        readIspfAttribute(task, parameters, "targetObject");
        readIspfAttribute(task, parameters, "function");

        String title = parameters.getOrDefault("title", task.getAttribute("name"));
        String instructions = parameters.getOrDefault("instructions", "");
        String assigneeRole = parameters.getOrDefault("assigneeRole", "operator");

        return new UserTaskDefinition(
                task.getAttribute("id"),
                task.getAttribute("name"),
                title,
                instructions,
                assigneeRole,
                Map.copyOf(parameters)
        );
    }

    private ServiceTaskDefinition parseServiceTask(Element task) throws WorkflowException {
        String id = task.getAttribute("id");
        String name = task.getAttribute("name");
        Map<String, String> parameters = new HashMap<>();

        readIspfAttribute(task, parameters, "action");
        readIspfAttribute(task, parameters, "targetObject");
        readIspfAttribute(task, parameters, "variable");
        readIspfAttribute(task, parameters, "value");
        readIspfAttribute(task, parameters, "subject");
        readIspfAttribute(task, parameters, "message");
        readIspfAttribute(task, parameters, "level");
        readIspfAttribute(task, parameters, "objectPath");
        readIspfAttribute(task, parameters, "functionName");
        readIspfAttribute(task, parameters, "inputMap");
        readIspfAttribute(task, parameters, "outputMap");
        readIspfAttribute(task, parameters, "eventName");
        readIspfAttribute(task, parameters, "payloadVariable");
        readIspfAttribute(task, parameters, "contextKey");
        readIspfAttribute(task, parameters, "sourceVariable");
        readIspfAttribute(task, parameters, "valueField");
        readIspfAttribute(task, parameters, "workflowPath");
        readIspfAttribute(task, parameters, "promptTemplate");
        readIspfAttribute(task, parameters, "outputVariable");
        readIspfAttribute(task, parameters, "outputFormat");
        readIspfAttribute(task, parameters, "modelRef");
        readIspfAttribute(task, parameters, "timeoutMs");
        readIspfAttribute(task, parameters, "agentMode");
        readIspfAttribute(task, parameters, "goalTemplate");
        readIspfAttribute(task, parameters, "toolAllowlist");
        readIspfAttribute(task, parameters, "maxSteps");
        readIspfAttribute(task, parameters, "retryable");

        Element extensions = firstChildElement(task, "extensionElements");
        if (extensions != null) {
            for (Element child : childElements(extensions)) {
                String localName = child.getLocalName() != null ? child.getLocalName() : child.getNodeName();
                if (localName.contains(":")) {
                    localName = localName.substring(localName.indexOf(':') + 1);
                }
                parameters.putIfAbsent(localName, child.getTextContent().trim());
            }
        }

        String actionRaw = parameters.getOrDefault("action", "log");
        WorkflowActionType action = parseAction(actionRaw);

        return new ServiceTaskDefinition(id, name, action, Map.copyOf(parameters));
    }

    private static void readIspfAttribute(Element element, Map<String, String> parameters, String name) {
        String value = readIspfAttribute(element, name);
        if (value != null && !value.isBlank()) {
            parameters.put(name, value);
        }
    }

    private static String readIspfAttribute(Element element, String name) {
        String value = element.getAttributeNS(ISPF_NS, name);
        if (value == null || value.isBlank()) {
            value = element.getAttribute("ispf:" + name);
        }
        return value;
    }

    private static Element firstElement(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            return null;
        }
        return (Element) nodes.item(0);
    }

    private static List<Element> elements(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getParentNode() == parent || isDirectChild(parent, node)) {
                result.add((Element) node);
            }
        }
        if (result.isEmpty()) {
            for (int i = 0; i < nodes.getLength(); i++) {
                result.add((Element) nodes.item(i));
            }
        }
        return result;
    }

    private static boolean isDirectChild(Element parent, Node node) {
        return node.getParentNode() != null && node.getParentNode().isSameNode(parent);
    }

    private static Element firstChildElement(Element parent, String localName) {
        for (Element child : childElements(parent)) {
            String name = child.getLocalName() != null ? child.getLocalName() : child.getNodeName();
            if (name.endsWith(localName)) {
                return child;
            }
        }
        return null;
    }

    private static List<Element> childElements(Element parent) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element) {
                children.add(element);
            }
        }
        return children;
    }

    private static WorkflowActionType parseAction(String actionRaw) throws WorkflowException {
        return switch (actionRaw.trim().toLowerCase(Locale.ROOT)) {
            case "log" -> WorkflowActionType.LOG;
            case "setvariable", "set_variable" -> WorkflowActionType.SET_VARIABLE;
            case "publishnats", "publish_nats" -> WorkflowActionType.PUBLISH_NATS;
            case "invokefunction", "invoke_function" -> WorkflowActionType.INVOKE_FUNCTION;
            case "fireevent", "fire_event" -> WorkflowActionType.FIRE_EVENT;
            case "readvariable", "read_variable" -> WorkflowActionType.READ_VARIABLE;
            case "startworkflow", "start_workflow" -> WorkflowActionType.START_WORKFLOW;
            case "llmcomplete", "llm_complete" -> WorkflowActionType.LLM_COMPLETE;
            case "invokeagent", "invoke_agent" -> WorkflowActionType.INVOKE_AGENT;
            default -> throw new WorkflowException("Unsupported service task action: " + actionRaw);
        };
    }
}
