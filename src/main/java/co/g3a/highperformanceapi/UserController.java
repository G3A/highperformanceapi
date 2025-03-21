package co.g3a.highperformanceapi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    // Almacena los emisores SSE activos
    private final Map<String, SseEmitter> sseEmitters = new ConcurrentHashMap<>();

    // Timeouts configurados para evitar conexiones pendientes innecesarias
    private static final long SSE_TIMEOUT = 300000L; // 5 minutos

    // Mapa para almacenar tareas completadas para recuperación posterior
    private final Map<UUID, Object> completedTasksCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> taskCompletionTimestamps = new ConcurrentHashMap<>();

    // Tiempo de expiración del caché (5 minutos)
    private static final long TASK_CACHE_EXPIRATION_MS = 300000;


    @GetMapping("hello")
    String helloWorld() throws InterruptedException {
        Thread.sleep(5000);
        return "Hello World from " + Thread.currentThread();
    }

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;

        // Configurar el servicio para notificar cuando las tareas se completan
        userService.setTaskCompletionCallback(this::notifyTaskCompletion);

        // Iniciar hilos de mantenimiento
        startStatsUpdateThread();
        startCacheCleanupThread();
        startHeartbeatThread();
    }

    @GetMapping("/by-id/{id}")
    public ResponseEntity<UserService.TaskResponse> getUserById(@PathVariable UUID id) {
        UserService.TaskResponse response = userService.getUserByIdAsync(id);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/by-username/{username}")
    public ResponseEntity<UserService.TaskResponse> getUserByUsername(@PathVariable String username) {
        UserService.TaskResponse response = userService.getUserByUsernameAsync(username);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/stats/submitted")
    public ResponseEntity<Long> getTotalTasksSubmitted() {
        return ResponseEntity.ok(userService.getTotalTasksSubmitted());
    }

    @GetMapping("/stats/completed")
    public ResponseEntity<Long> getTotalTasksCompleted() {
        return ResponseEntity.ok(userService.getTotalTasksCompleted());
    }

    @GetMapping("/stats/failed")
    public ResponseEntity<Long> getTotalTasksFailed() {
        return ResponseEntity.ok(userService.getTotalTasksFailed());
    }

    @GetMapping("/stats/running")
    public ResponseEntity<Integer> getRunningVirtualThreads() {
        return ResponseEntity.ok(userService.getRunningVirtualThreads());
    }

    @GetMapping("/stats/pending")
    public ResponseEntity<Integer> getPendingTaskCount() {
        return ResponseEntity.ok(userService.getPendingTaskCount());
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> healthInfo = new ConcurrentHashMap<>();
        healthInfo.put("totalTasksSubmitted", userService.getTotalTasksSubmitted());
        healthInfo.put("totalTasksCompleted", userService.getTotalTasksCompleted());
        healthInfo.put("totalTasksFailed", userService.getTotalTasksFailed());
        healthInfo.put("runningVirtualThreads", userService.getRunningVirtualThreads());
        healthInfo.put("pendingTasks", userService.getPendingTaskCount());

        return ResponseEntity.ok(healthInfo);
    }

    /**
     * Endpoint para establecer una conexión SSE con el cliente
     */
    @GetMapping(path = "/sse-connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connectSse() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        String clientId = UUID.randomUUID().toString();

        // Configurar callbacks para limpiar recursos cuando se cierra la conexión
        emitter.onCompletion(() -> sseEmitters.remove(clientId));
        emitter.onTimeout(() -> sseEmitters.remove(clientId));
        emitter.onError(e -> sseEmitters.remove(clientId));

        // Almacenar el emisor para usarlo más tarde
        sseEmitters.put(clientId, emitter);

        // Enviar un evento de conexión exitosa
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data(Map.of("status", "connected", "clientId", clientId)));

            // Enviar estadísticas iniciales
            sendStatsUpdate();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * Notifica a los clientes conectados cuando una tarea se completa y guarda en caché
     */
    private void notifyTaskCompletion(UUID taskId, User user, Throwable error) {
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

        if (!sseEmitters.isEmpty()) {
            // Preparar el evento según el resultado
            if (error == null && user != null) {
                // Tarea completada con éxito
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("taskId", taskId.toString());
                eventData.put("result", user);

                // Enviar a todos los clientes conectados
                sendEventToAllClients("task-completed", eventData);
            } else {
                // Error en la tarea
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("taskId", taskId.toString());
                eventData.put("error", error != null ? error.getMessage() : "Resultado nulo o desconocido");

                // Enviar a todos los clientes conectados
                sendEventToAllClients("task-error", eventData);
            }

            // Enviar actualización de estadísticas después de completar una tarea
            sendStatsUpdate();
        }
    }

    // Método auxiliar para enviar eventos a todos los clientes
    private void sendEventToAllClients(String eventName, Map<String, Object> data) {
        List<String> deadEmitters = new ArrayList<>();

        sseEmitters.forEach((clientId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (IOException e) {
                // Marcar este emitter para eliminación
                deadEmitters.add(clientId);
            }
        });

        // Eliminar emitters muertos
        deadEmitters.forEach(sseEmitters::remove);
    }

    /**
     * Envía actualizaciones de estadísticas a todos los clientes conectados
     */
    private void sendStatsUpdate() {
        if (sseEmitters.isEmpty()) {
            return; // No hay clientes conectados
        }

        Map<String, Object> statsData = Map.of(
                "totalTasksSubmitted", userService.getTotalTasksSubmitted(),
                "totalTasksCompleted", userService.getTotalTasksCompleted(),
                "totalTasksFailed", userService.getTotalTasksFailed(),
                "runningVirtualThreads", userService.getRunningVirtualThreads(),
                "pendingTasks", userService.getPendingTaskCount(),
                "timestamp", System.currentTimeMillis()
        );

        // Enviar a todos los clientes conectados
        sseEmitters.forEach((clientId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("stats-update")
                        .data(statsData));
            } catch (IOException e) {
                emitter.completeWithError(e);
                sseEmitters.remove(clientId);
            }
        });
    }

    /**
     * Inicia un hilo para enviar actualizaciones de estadísticas periódicamente
     */
    private void startStatsUpdateThread() {
        Thread statsThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Enviar actualizaciones cada 5 segundos
                    Thread.sleep(5000);
                    sendStatsUpdate();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        statsThread.setDaemon(true);
        statsThread.start();
    }


    /**
     * Endpoint para verificar el estado de una tarea específica
     */
    @GetMapping("/task-status/{taskId}")
    public ResponseEntity<?> getTaskStatus(@PathVariable UUID taskId) {
        // Verificar si la tarea está en el caché de tareas completadas
        if (completedTasksCache.containsKey(taskId)) {
            return ResponseEntity.ok(completedTasksCache.get(taskId));
        }

        // Verificar si la tarea está pendiente
        CompletableFuture<User> future = userService.getTaskResult(taskId);
        if (future != null && !future.isDone()) {
            return ResponseEntity.ok(Map.of(
                    "status", "pending",
                    "message", "La tarea está en proceso"
            ));
        }

        // La tarea no existe o ya expiró
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "status", "not_found",
                        "message", "Tarea no encontrada o expirada"
                ));
    }

    /**
     * Método para limpiar periódicamente el caché de tareas completadas
     */
    private void startCacheCleanupThread() {
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


    /**
     * Endpoint para confirmar la recepción de una tarea
     */
    @PostMapping("/confirm-receipt/{taskId}")
    public ResponseEntity<?> confirmTaskReceipt(@PathVariable UUID taskId) {
        // Lógica para confirmar la recepción de la tarea
        // ...
        // Registrar la confirmación (opcional: podrías eliminar la tarea del caché aquí)
        System.out.println("Cliente confirmó recepción de tarea: " + taskId);

        return ResponseEntity.ok(Map.of("status", "confirmed"));
    }


    private void startHeartbeatThread() {
        Thread heartbeatThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(30000); // Enviar heartbeat cada 30 segundos

                    // Enviar evento de heartbeat a todos los clientes conectados
                    sendEventToAllClients("heartbeat", Map.of(
                            "timestamp", System.currentTimeMillis()
                    ));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }
}