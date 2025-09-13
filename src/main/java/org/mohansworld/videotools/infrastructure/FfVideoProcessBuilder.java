package org.mohansworld.videotools.infrastructure;

import org.mohansworld.videotools.application.FfVideoProcessor;
import org.mohansworld.videotools.application.ProgressListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletionException;

/**
 * An implementation of FfVideoProcessor that executes ffmpeg/ffprobe commands
 * using Java's {@link ProcessBuilder}.
 * <p>
 * This class executes commands asynchronously on a dedicated thread pool and reports
 * progress and completion via the {@link ProgressListener}.
 * <p>
 * NOTE ON THREADING: This implementation is decoupled from any specific UI framework.
 * Progress updates via {@link ProgressListener} are invoked directly from a background thread.
 * If the listener interacts with a UI, it is the listener's responsibility to marshal
 * these calls onto the appropriate UI thread (e.g., using SwingUtilities.invokeLater or Platform.runLater).
 * <p>
 * This class is {@link AutoCloseable} and is responsible for managing its own thread pool.
 * It should be closed when no longer needed to ensure a clean shutdown.
 */
public class FfVideoProcessBuilder implements FfVideoProcessor, AutoCloseable {

    /**
     * A fixed-size thread pool is safer than a cached one to prevent resource exhaustion
     * if many commands are submitted concurrently. The size is based on the number of
     * available CPU cores, which is a sensible default for CPU/IO-bound tasks.
     */
    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    /**
     * A private record to hold the result of a process execution,
     * containing both the exit code and the combined output.
     */
    private record ProcessExecutionResult(int exitCode, String output) {}

    @Override
    public CompletableFuture<Integer> executeFfmpeg(List<String> command, ProgressListener listener) {
        // Chain off the common execution logic.
        // whenComplete is used for side-effects like logging, without changing the future's result.
        return executeCommand(command, listener)
                .whenComplete((result, ex) -> {
                    // This block handles reporting the final status to the listener.
                    if (ex != null) {
                        // The exception is already wrapped in CompletionException by our helper.
                        // We report the underlying cause.
                        listener.onError("An exception occurred: " + ex.getCause().getMessage());
                    } else if (result.exitCode() == 0) {
                        listener.onComplete("Operation completed successfully.");
                    } else {
                        // The full process output is available in result.output() and has already been
                        // streamed to the listener, so we just report the failure code.
                        listener.onError("Operation failed with exit code: " + result.exitCode());
                    }
                })
                // Transform the future's result from ProcessExecutionResult to the required Integer exit code.
                .thenApply(ProcessExecutionResult::exitCode);
    }

    @Override
    public CompletableFuture<String> executeFfprobe(List<String> command, ProgressListener listener) {
        // thenCompose is used to transform the result into a new future, which is
        // ideal for handling success and failure cases differently.
        return executeCommand(command, listener)
                .thenCompose(result -> {
                    if (result.exitCode() == 0) {
                        // On success, complete the future with the captured output string.
                        return CompletableFuture.completedFuture(result.output());
                    } else {
                        // On failure, create and return a failed future. This is more idiomatic
                        // than returning null and allows for better error handling by the caller.
                        String errorMessage = "ffprobe failed with exit code " + result.exitCode() + ". Output:\n" + result.output().trim();
                        listener.onError(errorMessage);
                        return CompletableFuture.failedFuture(new IOException(errorMessage));
                    }
                });
    }

    /**
     * Centralized private helper method to execute a command and read its output.
     * This eliminates code duplication and centralizes process management logic.
     *
     * @param command The command and its arguments to execute.
     * @param listener The listener for real-time progress updates.
     * @return A CompletableFuture that will complete with a ProcessExecutionResult.
     */
    private CompletableFuture<ProcessExecutionResult> executeCommand(List<String> command, ProgressListener listener) {
        listener.onProgress("Executing command: " + String.join(" ", command));
        listener.onProgress(""); // for spacing

        // CompletableFuture.supplyAsync is a clean way to run a task that returns a value.
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                // Merging error and input streams is crucial. It simplifies reading and
                // prevents potential deadlocks that can occur when reading two separate streams.
                pb.redirectErrorStream(true);

                Process process = pb.start();
                StringBuilder output = new StringBuilder();

                // try-with-resources ensures the reader is closed automatically.
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // Send real-time progress to the listener from this background thread.
                        listener.onProgress(line);
                        output.append(line).append(System.lineSeparator());
                    }
                }

                int exitCode = process.waitFor();
                return new ProcessExecutionResult(exitCode, output.toString());

            } catch (IOException | InterruptedException e) {
                // When an exception occurs in supplyAsync, it's best to wrap it
                // in a CompletionException to work smoothly with the CompletableFuture API.
                Thread.currentThread().interrupt(); // Preserve the interrupted status
                throw new CompletionException(e);
            }
        }, executor); // IMPORTANT: Use our managed executor for the final handling step.
    }

    /**
     * Shuts down the executor service. This method should be called when the
     * application is closing to ensure a clean exit.
     */
    @Override
    public void close() {
        // A graceful shutdown of the managed thread pool.
        executor.shutdown();
    }
}