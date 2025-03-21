package co.g3a.highperformanceapi.features.user;

import co.g3a.highperformanceapi.features.shared.TriConsumer;
import co.g3a.highperformanceapi.features.shared.User;
import co.g3a.highperformanceapi.features.tasks.TaskResponse;
import co.g3a.highperformanceapi.features.tasks.TaskService;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class UserService {

    private final Map<UUID, User> userDatabase = new ConcurrentHashMap<>();
    private final Map<String, UUID> usernameIndex = new ConcurrentHashMap<>();
    private final TaskService taskService;

    @Setter
    private TriConsumer<UUID, User, Throwable> taskCompletionCallback;

    public UserService(TaskService taskService) {
        // Precargar usuarios y crear índice por username
        for (int i = 0; i < 1000; i++) {
            UUID id = UUID.randomUUID();
            String username = "user" + i;
            userDatabase.put(id, new User(
                    id,
                    username,
                    username + "@example.com",
                    "User " + i
            ));
            usernameIndex.put(username, id);
        }
        this.taskService = taskService;
    }


    public TaskResponse getUserByIdAsync(UUID id) {
        UUID taskId = UUID.randomUUID();

        taskService.getTotalTasksSubmitted().incrementAndGet();

        CompletableFuture<User> future = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(5000); // Simulación de tiempo de procesamiento

                User user = userDatabase.get(id);
                if (user == null) {
                    throw new RuntimeException("User not found with id: " + id);
                }
                return user;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Task interrupted", e);
            }
        });

        future.whenComplete((user, throwable) -> {
            try {
                if (throwable != null) {
                    taskService.getTotalTasksFailed().incrementAndGet();
                } else {
                    taskService.getTotalTasksCompleted().incrementAndGet();
                }

                if (taskCompletionCallback != null) {
                    taskCompletionCallback.accept(taskId, user, throwable);
                }
            } catch (Exception e) {
                System.err.println("Error en callback de tarea: " + e.getMessage());
                e.printStackTrace();
            } finally {
                taskService.getPendingTasks().remove(taskId);
            }
        });

        taskService.getPendingTasks().put(taskId, future);
        return new TaskResponse(taskId, "ACCEPTED");
    }

    public TaskResponse getUserByUsernameAsync(String username) {
        UUID taskId = UUID.randomUUID();
        taskService.getTotalTasksSubmitted().incrementAndGet();

        CompletableFuture<User> future = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(1000); // Simulación de tiempo de procesamiento

                UUID userId = usernameIndex.get(username);
                if (userId == null) {
                    throw new RuntimeException("User not found with username: " + username);
                }
                User user = userDatabase.get(userId);
                if (user == null) {
                    throw new RuntimeException("User not found with username: " + username);
                }
                return user;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Task interrupted", e);
            }
        });

        future.whenComplete((user, throwable) -> {
            try {
                if (throwable != null) {
                    taskService.getTotalTasksFailed().incrementAndGet();
                } else {
                    taskService.getTotalTasksCompleted().incrementAndGet();
                }

                if (taskCompletionCallback != null) {
                    taskCompletionCallback.accept(taskId, user, throwable);
                }
            } catch (Exception e) {
                System.err.println("Error en callback de tarea: " + e.getMessage());
                e.printStackTrace();
            } finally {
                taskService.getPendingTasks().remove(taskId);
            }
        });

        taskService.getPendingTasks().put(taskId, future);
        return new TaskResponse(taskId, "ACCEPTED");
    }

}