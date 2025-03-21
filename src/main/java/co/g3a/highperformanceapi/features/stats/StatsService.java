package co.g3a.highperformanceapi.features.stats;

import co.g3a.highperformanceapi.features.tasks.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StatsService {
    // Almacena los emisores SSE activos
    private final Map<String, SseEmitter> sseEmitters = new ConcurrentHashMap<>();
    private final TaskService taskService;

    public StatsService(TaskService taskService) {
         this.taskService = taskService;
    }

    /**
     * Inicia un hilo para enviar actualizaciones de estadísticas periódicamente
     */
    public void startStatsUpdateThread() {
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
     * Envía actualizaciones de estadísticas a todos los clientes conectados
     */
    public void sendStatsUpdate() {
        if (sseEmitters.isEmpty()) {
            return; // No hay clientes conectados
        }

        Map<String, Object> statsData = Map.of(
                "totalTasksSubmitted", taskService.getTotalTasksSubmitted(),
                "totalTasksCompleted", taskService.getTotalTasksCompleted(),
                "totalTasksFailed", taskService.getTotalTasksFailed(),
                "runningVirtualThreads", taskService.getRunningVirtualThreads(),
                "pendingTasks", taskService.getPendingTaskCount(),
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
}