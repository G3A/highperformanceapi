package co.g3a.highperformanceapi.features.sse;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Getter
@Setter
public class SseService {

    // Almacena los emisores SSE activos
    private final Map<String, SseEmitter> sseEmitters = new ConcurrentHashMap<>();


    // Método auxiliar para enviar eventos a todos los clientes
    public void sendEventToAllClients(String eventName, Map<String, Object> data) {
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
}
