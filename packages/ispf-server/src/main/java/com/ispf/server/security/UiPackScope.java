package com.ispf.server.security;

/**
 * ADR-0073: app id carried on a pack-scoped authentication ({@code uiPackAppId}).
 */
public record UiPackScope(String appId) {
}
