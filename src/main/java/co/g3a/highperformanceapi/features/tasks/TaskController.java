package co.g3a.highperformanceapi.features.tasks;

import co.g3a.highperformanceapi.features.shared.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /**
     * Endpoint para verificar el estado de una tarea específica
     */
    @GetMapping("/task-status/{taskId}")
    public ResponseEntity<?> getTaskStatus(@PathVariable UUID taskId) {
        // Verificar si la tarea está en el caché de tareas completadas
        if (taskService.getCompletedTasksCache().containsKey(taskId)) {
            return ResponseEntity.ok(taskService.getCompletedTasksCache().get(taskId));
        }

        // Verificar si la tarea está pendiente
        CompletableFuture<User> future = taskService.getTaskResult(taskId);
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
}
