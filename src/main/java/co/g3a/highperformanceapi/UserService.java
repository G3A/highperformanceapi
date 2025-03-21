package co.g3a.highperformanceapi;

import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UserService {

    private final Map<UUID, User> userDatabase = new ConcurrentHashMap<>();
    private final Map<String, UUID> usernameIndex = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<User>> pendingTasks = new ConcurrentHashMap<>();
    private final AtomicLong totalTasksSubmitted = new AtomicLong(0);
    private final AtomicLong totalTasksCompleted = new AtomicLong(0);
    private final AtomicLong totalTasksFailed = new AtomicLong(0);

    private TriConsumer<UUID, User, Throwable> taskCompletionCallback;

    public UserService() {
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
    }

    public void setTaskCompletionCallback(TriConsumer<UUID, User, Throwable> callback) {
        this.taskCompletionCallback = callback;
    }

    public static class TaskResponse {
        private final UUID taskId;
        private final String status;

        public TaskResponse(UUID taskId, String status) {
            this.taskId = taskId;
            this.status = status;
        }

        public UUID getTaskId() {
            return taskId;
        }

        public String getStatus() {
            return status;
        }
    }

    public TaskResponse getUserByIdAsync(UUID id) {
        UUID taskId = UUID.randomUUID();
        totalTasksSubmitted.incrementAndGet();

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
                    totalTasksFailed.incrementAndGet();
                } else {
                    totalTasksCompleted.incrementAndGet();
                }

                if (taskCompletionCallback != null) {
                    taskCompletionCallback.accept(taskId, user, throwable);
                }
            } catch (Exception e) {
                System.err.println("Error en callback de tarea: " + e.getMessage());
                e.printStackTrace();
            } finally {
                pendingTasks.remove(taskId);
            }
        });

        pendingTasks.put(taskId, future);
        return new TaskResponse(taskId, "ACCEPTED");
    }

    public TaskResponse getUserByUsernameAsync(String username) {
        UUID taskId = UUID.randomUUID();
        totalTasksSubmitted.incrementAndGet();

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
                    totalTasksFailed.incrementAndGet();
                } else {
                    totalTasksCompleted.incrementAndGet();
                }

                if (taskCompletionCallback != null) {
                    taskCompletionCallback.accept(taskId, user, throwable);
                }
            } catch (Exception e) {
                System.err.println("Error en callback de tarea: " + e.getMessage());
                e.printStackTrace();
            } finally {
                pendingTasks.remove(taskId);
            }
        });

        pendingTasks.put(taskId, future);
        return new TaskResponse(taskId, "ACCEPTED");
    }

    public CompletableFuture<User> getTaskResult(UUID taskId) {
        CompletableFuture<User> future = pendingTasks.get(taskId);
        if (future == null) {
            CompletableFuture<User> notFound = new CompletableFuture<>();
            notFound.completeExceptionally(new RuntimeException("Task not found or already completed"));
            return notFound;
        }
        return future;
    }

    public long getTotalTasksSubmitted() {
        return totalTasksSubmitted.get();
    }

    public long getTotalTasksCompleted() {
        return totalTasksCompleted.get();
    }

    public long getTotalTasksFailed() {
        return totalTasksFailed.get();
    }

    public int getRunningVirtualThreads() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        return threadMXBean.getDaemonThreadCount();
    }

    public int getPendingTaskCount() {
        return pendingTasks.size();
    }

    public void shutdown() {
        // No es necesario apagar explícitamente los virtual threads
    }

    @FunctionalInterface
    public interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }
}