package co.g3a.highperformanceapi;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

@Component
public class RequestQueue {

    private static final int BUFFER_SIZE = 1024 * 1024; // Tamaño del buffer para 1M elementos
    private Disruptor<RequestEvent> disruptor;
    private RingBuffer<RequestEvent> ringBuffer;

    @PostConstruct
    public void init() {
        // Creamos un Disruptor usando Virtual Threads de Java 21
        disruptor = new Disruptor<>(
                RequestEvent::new,
                BUFFER_SIZE,
                r -> Thread.ofVirtual().name("worker-" + r).unstarted(r)
        );

        // Configuramos los handlers que procesarán los eventos
        disruptor.handleEventsWith(this::processEvent);

        // Iniciamos el disruptor
        disruptor.start();

        ringBuffer = disruptor.getRingBuffer();
    }

    private void processEvent(RequestEvent event, long sequence, boolean endOfBatch) {
        try {
            // Usamos un tipo raw (sin verificación de tipos) para ejecutar la tarea
            // Esto es seguro porque sabemos que el future y la tarea son compatibles
            // cuando se envían a través de submit()
            Supplier<?> task = event.getTask();
            Object result = task.get();

            // Completamos el future usando el método unchecked
            CompletableFuture future = event.getFuture();
            future.complete(result);
        } catch (Exception e) {
            // Si hay error, lo propagamos al future
            event.getFuture().completeExceptionally(e);
        } finally {
            // Limpiamos el evento para permitir su reutilización
            event.clear();
        }
    }

    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();

        // Publicamos la tarea en el ring buffer
        long sequence = ringBuffer.next();
        try {
            RequestEvent event = ringBuffer.get(sequence);
            event.setTask(task);
            event.setFuture(future);
        } finally {
            ringBuffer.publish(sequence);
        }

        return future;
    }

    @PreDestroy
    public void shutdown() {
        if (disruptor != null) {
            disruptor.shutdown();
        }
    }

    // Clase interna para representar los eventos en la cola
    public static class RequestEvent {
        private Supplier<?> task;
        private CompletableFuture<?> future;

        public void setTask(Supplier<?> task) {
            this.task = task;
        }

        public void setFuture(CompletableFuture<?> future) {
            this.future = future;
        }

        public Supplier<?> getTask() {
            return task;
        }

        public CompletableFuture<?> getFuture() {
            return future;
        }

        public void clear() {
            this.task = null;
            this.future = null;
        }
    }
}