package co.g3a.highperformanceapi.features.stats;

import co.g3a.highperformanceapi.features.tasks.TaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/users")
public class StatsController {

    private final TaskService taskService;



    public StatsController(TaskService taskService, StatsService statsService) {
        this.taskService = taskService;

        // Iniciar hilos de mantenimiento
        statsService.startStatsUpdateThread();
    }

    @GetMapping("/stats/submitted")
    public ResponseEntity<AtomicLong> getTotalTasksSubmitted() {
        return ResponseEntity.ok(taskService.getTotalTasksSubmitted());
    }

    @GetMapping("/stats/completed")
    public ResponseEntity<AtomicLong> getTotalTasksCompleted() {
        return ResponseEntity.ok(taskService.getTotalTasksCompleted());
    }

    @GetMapping("/stats/failed")
    public ResponseEntity<AtomicLong> getTotalTasksFailed() {
        return ResponseEntity.ok(taskService.getTotalTasksFailed());
    }

    @GetMapping("/stats/running")
    public ResponseEntity<Integer> getRunningVirtualThreads() {
        return ResponseEntity.ok(taskService.getRunningVirtualThreads());
    }

    @GetMapping("/stats/pending")
    public ResponseEntity<Integer> getPendingTaskCount() {
        return ResponseEntity.ok(taskService.getPendingTaskCount());
    }

    @GetMapping("/stats/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> healthInfo = new ConcurrentHashMap<>();
        healthInfo.put("totalTasksSubmitted", taskService.getTotalTasksSubmitted());
        healthInfo.put("totalTasksCompleted", taskService.getTotalTasksCompleted());
        healthInfo.put("totalTasksFailed", taskService.getTotalTasksFailed());
        healthInfo.put("runningVirtualThreads", taskService.getRunningVirtualThreads());
        healthInfo.put("pendingTasks", taskService.getPendingTaskCount());

        return ResponseEntity.ok(healthInfo);
    }




}