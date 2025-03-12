package co.g3a.highperformanceapi;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public DeferredResult<ResponseEntity<User>> getUserById(@PathVariable String id) {
        DeferredResult<ResponseEntity<User>> result = new DeferredResult<>(30000L); // 30 segundos timeout
        
        CompletableFuture<User> future = userService.getUserById(UUID.fromString(id));
        
        future.thenAccept(user -> result.setResult(ResponseEntity.ok(user)))
              .exceptionally(ex -> {
                  result.setResult(ResponseEntity.notFound().build());
                  return null;
              });
        
        return result;
    }

    @GetMapping("/by-username/{username}")
    public DeferredResult<ResponseEntity<User>> getUserByUsername(@PathVariable String username) {
        DeferredResult<ResponseEntity<User>> result = new DeferredResult<>(30000L);
        
        CompletableFuture<User> future = userService.getUserByUsername(username);
        
        future.thenAccept(user -> result.setResult(ResponseEntity.ok(user)))
              .exceptionally(ex -> {
                  result.setResult(ResponseEntity.notFound().build());
                  return null;
              });
        
        return result;
    }

    @GetMapping("/by-username-sincrono/{username}")
    public ResponseEntity<User> getUserByUsernameSincrono(@PathVariable String username) {
        User userByUsernameSincrono = userService.getUserByUsernameSincrono(username);
        return ResponseEntity.ok(userService.getUserByUsernameSincrono(username));
    }
}