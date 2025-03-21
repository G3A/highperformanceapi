package co.g3a.highperformanceapi.features.sse;

import co.g3a.highperformanceapi.features.stats.StatsService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class SseController {

    // Timeouts configurados para evitar conexiones pendientes innecesarias
    private static final long SSE_TIMEOUT = 300000L; // 5 minutos

    private final SseService sseService;
    private final StatsService statsService;

    public SseController(SseService sseService,StatsService statsService){
        this.sseService = sseService;
        this.statsService = statsService;
    }

    /**
     * Endpoint para establecer una conexión SSE con el cliente
     */
    @GetMapping(path = "/sse-connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connectSse() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        String clientId = UUID.randomUUID().toString();

        // Configurar callbacks para limpiar recursos cuando se cierra la conexión
        emitter.onCompletion(() -> sseService.getSseEmitters().remove(clientId));
        emitter.onTimeout(() -> sseService.getSseEmitters().remove(clientId));
        emitter.onError(e -> sseService.getSseEmitters().remove(clientId));

        // Almacenar el emisor para usarlo más tarde
        sseService.getSseEmitters().put(clientId, emitter);

        // Enviar un evento de conexión exitosa
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data(Map.of("status", "connected", "clientId", clientId)));

            // Enviar estadísticas iniciales
            statsService.sendStatsUpdate();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }
}
