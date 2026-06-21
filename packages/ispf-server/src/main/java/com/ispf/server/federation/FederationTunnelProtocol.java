package com.ispf.server.federation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.UUID;

public final class FederationTunnelProtocol {

    public static final String TYPE_REGISTER = "register";
    public static final String TYPE_REGISTERED = "registered";
    public static final String TYPE_PING = "ping";
    public static final String TYPE_PONG = "pong";
    public static final String TYPE_TOKEN_REFRESH = "token_refresh";
    public static final String TYPE_PROXY_REQUEST = "proxy_request";
    public static final String TYPE_PROXY_RESPONSE = "proxy_response";
    public static final String TYPE_EVENT_NOTIFY = "event_notify";
    public static final int PROTOCOL_VERSION = 1;

    private FederationTunnelProtocol() {
    }

    public static String register(String siteName, String pathPrefix, ObjectMapper mapper) throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", TYPE_REGISTER);
        node.put("protocolVersion", PROTOCOL_VERSION);
        node.put("siteName", siteName);
        node.put("pathPrefix", pathPrefix == null ? "root.platform" : pathPrefix);
        return mapper.writeValueAsString(node);
    }

    public static String registered(UUID peerId, String token, String tokenExpiresAt, ObjectMapper mapper)
            throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", TYPE_REGISTERED);
        node.put("peerId", peerId.toString());
        node.put("token", token);
        if (tokenExpiresAt != null) {
            node.put("tokenExpiresAt", tokenExpiresAt);
        }
        return mapper.writeValueAsString(node);
    }

    public static String ping(ObjectMapper mapper) throws Exception {
        return mapper.writeValueAsString(Map.of("type", TYPE_PING));
    }

    public static String pong(ObjectMapper mapper) throws Exception {
        return mapper.writeValueAsString(Map.of("type", TYPE_PONG));
    }

    public static String tokenRefresh(String token, String tokenExpiresAt, ObjectMapper mapper) throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", TYPE_TOKEN_REFRESH);
        node.put("token", token);
        if (tokenExpiresAt != null) {
            node.put("tokenExpiresAt", tokenExpiresAt);
        }
        return mapper.writeValueAsString(node);
    }

    public static String proxyRequest(String id, String method, String path, String query, String body, ObjectMapper mapper)
            throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", TYPE_PROXY_REQUEST);
        node.put("id", id);
        node.put("method", method);
        node.put("path", path);
        if (query != null) {
            node.put("query", query);
        }
        if (body != null && !body.isBlank()) {
            node.set("body", mapper.readTree(body));
        }
        return mapper.writeValueAsString(node);
    }

    public static String proxyResponse(String id, int status, JsonNode body, ObjectMapper mapper) throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", TYPE_PROXY_RESPONSE);
        node.put("id", id);
        node.put("status", status);
        if (body != null) {
            node.set("body", body);
        }
        return mapper.writeValueAsString(node);
    }

    public static String eventNotify(String path, String variableName, ObjectMapper mapper) throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", TYPE_EVENT_NOTIFY);
        node.put("path", path);
        if (variableName != null) {
            node.put("variableName", variableName);
        }
        return mapper.writeValueAsString(node);
    }
}
