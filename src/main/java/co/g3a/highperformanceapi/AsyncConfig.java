package co.g3a.highperformanceapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executors;

//@Configuration
//@EnableAsync
public class AsyncConfig {

    //@Bean
    public AsyncTaskExecutor applicationTaskExecutor() {
        // Usar Virtual Threads de Java 21 para el procesamiento asíncrono
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}