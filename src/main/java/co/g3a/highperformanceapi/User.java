package co.g3a.highperformanceapi;

import java.util.UUID;

public record User(UUID id, String username, String email, String fullName) {
}