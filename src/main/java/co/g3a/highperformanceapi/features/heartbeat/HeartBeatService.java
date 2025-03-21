package co.g3a.highperformanceapi.features.heartbeat;

import co.g3a.highperformanceapi.features.sse.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class HeartBeatService {

    private final SseService sseService;

    public void startHeartbeatThread() {
        Thread heartbeatThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(30000); // Enviar heartbeat cada 30 segundos

                    // Enviar evento de heartbeat a todos los clientes conectados
                    sseService.sendEventToAllClients("heartbeat", Map.of(
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
