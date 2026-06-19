package cn.bitloom.util;

import lombok.Getter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The type Thread pool manager util.
 *
 * @author bitloom
 */
public class ExecutorManager {

    @Getter
    private static final ExecutorService platformTaskExecutor = Executors.newFixedThreadPool(5);
    @Getter
    private static final ExecutorService teammateExecutor = Executors.newFixedThreadPool(5);

    /**
     * Close.
     */
    public static void close(){
        platformTaskExecutor.shutdown();
        teammateExecutor.shutdown();
        try {
            if (!platformTaskExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                platformTaskExecutor.shutdownNow();
            }
            if (!teammateExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                teammateExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            platformTaskExecutor.shutdownNow();
            teammateExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
