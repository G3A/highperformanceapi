package co.g3a.highperformanceapi.features.user;

import co.g3a.highperformanceapi.features.tasks.TaskResponse;
import co.g3a.highperformanceapi.features.tasks.TaskService;
import co.g3a.highperformanceapi.features.heartbeat.HeartBeatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService, TaskService taskService, HeartBeatService heartBeatService) {
        this.userService = userService;

        // Configurar el servicio para notificar cuando las tareas se completan
        userService.setTaskCompletionCallback(taskService::notifyTaskCompletion);

        // Iniciar hilos de mantenimiento
        taskService.startTasksCacheCleanupThread();

        heartBeatService.startHeartbeatThread();
    }

    @GetMapping("hello")
    String helloWorld() throws InterruptedException {
        Thread.sleep(5000);
        return "Hello World from " + Thread.currentThread();
    }

    @GetMapping("/by-id/{id}")
    public ResponseEntity<TaskResponse> getUserById(@PathVariable UUID id) {
        TaskResponse response = userService.getUserByIdAsync(id);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/by-username/{username}")
    public ResponseEntity<TaskResponse> getUserByUsername(@PathVariable String username) {
        TaskResponse response = userService.getUserByUsernameAsync(username);
        return ResponseEntity.accepted().body(response);
    }
}