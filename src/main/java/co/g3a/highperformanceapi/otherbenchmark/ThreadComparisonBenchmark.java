package co.g3a.highperformanceapi.otherbenchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

public class ThreadComparisonBenchmark {

    public static void main(String[] args) {
        int taskCount = 1_000_000; // Number of tasks
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Virtual Thread Executor
        var executor = Executors.newVirtualThreadPerTaskExecutor();

        long start = System.currentTimeMillis();

        for (int i = 0; i < taskCount; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(100); // Simulate I/O task
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, executor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long end = System.currentTimeMillis();

        System.out.println("Time taken with Virtual Threads: " + (end - start) + " ms");
        executor.close();
    }
}
