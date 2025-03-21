package co.g3a.highperformanceapi.features.tasks;

import co.g3a.highperformanceapi.features.shared.User;
import co.g3a.highperformanceapi.features.sse.SseService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Getter
@RequiredArgsConstructor
public class TaskService {

    // Mapa para almacenar tareas completadas para recuperación posterior
    private final Map<UUID, Object> completedTasksCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> taskCompletionTimestamps = new ConcurrentHashMap<>();

    private final SseService sseService;
    //private final StatsService statsService;

    // Tiempo de expiración del caché (5 minutos)
    private static final long TASK_CACHE_EXPIRATION_MS = 300000;

    private final Map<UUID, CompletableFuture<User>> pendingTasks = new ConcurrentHashMap<>();
    private final AtomicLong totalTasksSubmitted = new AtomicLong(0);
    private final AtomicLong totalTasksCompleted = new AtomicLong(0);
    private final AtomicLong totalTasksFailed = new AtomicLong(0);


    /**
     * Notifica a los clientes conectados cuando una tarea se completa y guarda en caché
     */
    public void notifyTaskCompletion(UUID taskId, User user, Throwable error) {
        // Guardar el resultado en caché para recuperación posterior
        if (error == null && user != null) {
            // Tarea completada con éxito
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("result", user);
            resultData.put("status", "success");

            completedTasksCache.put(taskId, resultData);
            taskCompletionTimestamps.put(taskId, System.currentTimeMillis());
        } else {
            // Error en la tarea
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("error", error != null ? error.getMessage() : "Resultado nulo o desconocido");
            resultData.put("status", "error");

            completedTasksCache.put(taskId, resultData);
            taskCompletionTimestamps.put(taskId, System.currentTimeMillis());
        }

        if (!sseService.getSseEmitters().isEmpty()) {
            // Preparar el evento según el resultado
            if (error == null && user != null) {
                // Tarea completada con éxito
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("taskId", taskId.toString());
                eventData.put("result", user);

                // Enviar a todos los clientes conectados
                sseService.sendEventToAllClients("task-completed", eventData);
            } else {
                // Error en la tarea
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("taskId", taskId.toString());
                eventData.put("error", error != null ? error.getMessage() : "Resultado nulo o desconocido");

                // Enviar a todos los clientes conectados
                sseService.sendEventToAllClients("task-error", eventData);
            }

            // Enviar actualización de estadísticas después de completar una tarea
            //statsService.sendStatsUpdate();
        }
    }



    /**
     * Método para limpiar periódicamente el caché de tareas completadas
     */
    public void startTasksCacheCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60000); // Verificar cada minuto

                    long now = System.currentTimeMillis();
                    List<UUID> expiredTasks = new ArrayList<>();

                    taskCompletionTimestamps.forEach((taskId, timestamp) -> {
                        if (now - timestamp > TASK_CACHE_EXPIRATION_MS) {
                            expiredTasks.add(taskId);
                        }
                    });

                    // Eliminar tareas expiradas
                    for (UUID taskId : expiredTasks) {
                        completedTasksCache.remove(taskId);
                        taskCompletionTimestamps.remove(taskId);
                    }

                    if (!expiredTasks.isEmpty()) {
                        System.out.println("Se eliminaron " + expiredTasks.size() + " tareas expiradas del caché");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        cleanupThread.setDaemon(true);
        cleanupThread.start();
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



    public int getRunningVirtualThreads() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        return threadMXBean.getDaemonThreadCount();
    }

    public int getPendingTaskCount() {
        return pendingTasks.size();
    }


}
