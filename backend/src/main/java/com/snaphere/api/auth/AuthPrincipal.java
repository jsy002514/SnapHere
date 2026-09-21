package com.snaphere.api.auth;
import java.util.UUID;
public record AuthPrincipal(UUID userId, UUID deviceId, UserRole role) {}
