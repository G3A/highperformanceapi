package co.g3a.highperformanceapi.features.shared;

import java.util.UUID;

public record User(UUID id, String username, String email, String name) {
}