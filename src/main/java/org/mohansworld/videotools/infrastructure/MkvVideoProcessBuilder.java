package org.mohansworld.videotools.infrastructure;

import org.mohansworld.videotools.application.MkvVideoProcessor;
import org.mohansworld.videotools.application.ProgressListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * An implementation of MkvVideoProcessor that executes external MKVToolNix commands
 * using Java's ProcessBuilder.
 *
 * <p>This implementation is fully asynchronous and non-blocking. It handles process
 * execution, concurrent consumption of standard output and error streams to prevent
 * deadlocks, and reports progress back through a ProgressListener.</p>
 *
 * <p>This class is {@link AutoCloseable} and is responsible for managing its own thread pool.
 * It should be closed when no longer needed to ensure a clean shutdown. Callers should
 * use a try-with-resources block or manually call the {@link #close()} method.</p>
 */
public class MkvVideoProcessBuilder implements MkvVideoProcessor, AutoCloseable {

    /**
     * A fixed-size thread pool is safer than a cached one to prevent resource exhaustion
     * if many commands are submitted concurrently. The size is based on the number of
     * available CPU cores, which is a sensible default for CPU/IO-bound tasks.
     */
    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    /**
     * mkvmerge exits with 0 on successful completion without any warnings.
     */
    private static final int MKVTOOLNIX_SUCCESS_EXIT_CODE = 0;
    /**
     * mkvmerge exits with 1 when warnings were generated but the operation was otherwise successful.
     */
    private static final int MKVTOOLNIX_WARNING_EXIT_CODE = 1;
    private static final String MKVTOOLNIX_WARNING_MARKER = "Warning:";


    /**
     * Asynchronously executes a command-line process (typically for MKVToolNix).
     *
     * @param command  A list of strings representing the command and its arguments.
     * @param listener A listener to receive progress updates, completion notifications, or errors.
     * @return A {@link CompletableFuture} which will complete with the standard output of the process
     *         if successful, or complete exceptionally if an error occurs.
     */
    @Override
    public CompletableFuture<String> executeMkv(List<String> command, ProgressListener listener) {
        listener.onProgress("Executing the following command:");
        listener.onProgress(String.join(" ", command));
        listener.onProgress("");

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();

            // To prevent deadlocks, we must consume both stdout and stderr concurrently.
            // We create separate futures for consuming each stream on our managed executor.
            // Note: mkvmerge often prints progress to stderr, so we report it via onProgress.
            CompletableFuture<Void> stdOutFuture = consumeStream(process.getInputStream(), output, listener::onProgress);
            CompletableFuture<Void> stdErrFuture = consumeStream(process.getErrorStream(), error, listener::onProgress);

            // We now orchestrate the completion. The final result depends on three things:
            // 1. The process must terminate (onExit).
            // 2. The standard output stream must be fully consumed.
            // 3. The standard error stream must be fully consumed.
            // CompletableFuture.allOf ensures we wait for all of them to finish.
            return CompletableFuture.allOf(stdOutFuture, stdErrFuture, process.onExit())
                    .handleAsync((v, throwable) -> {
                        // This block executes on our managed executor after the process has exited and streams are closed.
                        if (throwable != null) {
                            // This would catch exceptions from the stream consumers, if any.
                            String errorMessage = "Failed while processing command output. Details: " + throwable.getMessage();
                            listener.onError(errorMessage);
                            throw new RuntimeException(errorMessage, throwable);
                        }

                        int exitCode = process.exitValue();
                        String errorOutput = error.toString();

                        if (isSuccessfulExit(exitCode, errorOutput)) {
                            listener.onComplete("Operation completed successfully.");
                            return output.toString();
                        } else {
                            String errorMessage = buildErrorMessage(exitCode, errorOutput);
                            listener.onError(errorMessage);
                            throw new RuntimeException(errorMessage);
                        }
                    }, executor); // IMPORTANT: Use our managed executor for the final handling step.

        } catch (IOException e) {
            // This catches errors from processBuilder.start(), e.g., command not found.
            String errorMessage = "Failed to start command. Ensure MKVToolNix is installed and in your system's PATH. Details: " + e.getMessage();
            listener.onError(errorMessage);
            // Return a future that is already completed exceptionally.
            return CompletableFuture.failedFuture(new RuntimeException(errorMessage, e));
        }
    }

    /**
     * Consumes an InputStream in a separate thread and appends its content to a StringBuilder.
     *
     * @param is        The InputStream to consume (e.g., process.getInputStream()).
     * @param buffer    The StringBuilder to store the stream's content.
     * @param onNewLine A consumer that gets called for each line read from the stream.
     * @return A {@link CompletableFuture} that completes when the stream has been fully read.
     */
    private CompletableFuture<Void> consumeStream(InputStream is, StringBuilder buffer, Consumer<String> onNewLine) {
        // Run this I/O-bound task on our managed executor.
        return CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line).append(System.lineSeparator());
                    onNewLine.accept(line);
                }
            } catch (IOException e) {
                // This exception is unlikely but should be handled.
                // It will be propagated to the final .handleAsync block.
                throw new RuntimeException("Failed to read process stream", e);
            }
        }, executor); // IMPORTANT: Specify the executor to use.
    }

    /**
     * Determines if the process exit code signifies a successful operation for mkvmerge.
     * mkvmerge can exit with 1 for warnings, which we consider a success.
     */
    private boolean isSuccessfulExit(int exitCode, String errorOutput) {
        return exitCode == MKVTOOLNIX_SUCCESS_EXIT_CODE ||
                (exitCode == MKVTOOLNIX_WARNING_EXIT_CODE && errorOutput.contains(MKVTOOLNIX_WARNING_MARKER));
    }

    /**
     * Builds a detailed error message based on the exit code and error stream content.
     */
    private String buildErrorMessage(int exitCode, String errorOutput) {
        String message = "Operation failed with exit code " + exitCode + ".";
        if (errorOutput != null && !errorOutput.trim().isEmpty()) {
            message += "\nDetails:\n" + errorOutput.trim();
        }
        return message;
    }

    /**
     * Shuts down the executor service. This method should be called when this
     * object is no longer needed to ensure a clean exit of its background threads.
     */
    @Override
    public void close() {
        // A graceful shutdown of the managed thread pool.
        executor.shutdown();
    }
}