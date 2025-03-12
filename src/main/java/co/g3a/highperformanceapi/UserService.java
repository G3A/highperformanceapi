package co.g3a.highperformanceapi;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    private final Map<UUID, User> userDatabase = new ConcurrentHashMap<>();
    private final RequestQueue requestQueue;

    public UserService(RequestQueue requestQueue) {
        this.requestQueue = requestQueue;

        // Precargar algunos usuarios para pruebas
        for (int i = 0; i < 1000; i++) {
            UUID id = UUID.randomUUID();
            userDatabase.put(id, new User(
                    id,
                    "user" + i,
                    "user" + i + "@example.com",
                    "User " + i
            ));
        }
    }

    public CompletableFuture<User> getUserById(UUID id) {
        return requestQueue.submit(() -> {
            User user = userDatabase.get(id);
            if (user == null) {
                throw new RuntimeException("User not found with id: " + id);
            }
            return user;
        });
    }

    public CompletableFuture<User> getUserByUsername(String username) {
        // Emular 2 segundos de procesamiento
        try {
            Thread.sleep(5000);

            return requestQueue.submit(() ->
                    userDatabase.values().stream()
                            .filter(user -> user.username().equals(username))
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("User not found with username: " + username))
            );
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public User getUserByUsernameSincrono(String username) {
        // Emular 2 segundos de procesamiento
        try {
            Thread.sleep(5000);

            return userDatabase.values().stream()
                    .filter(user -> user.username().equals(username))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("User not found with username: " + username));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
