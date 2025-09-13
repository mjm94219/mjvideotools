package org.mohansworld.videotools.application;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * An abstraction for executing command-line MKV tools (like mkvmerge, mkvinfo, etc.)
 * asynchronously.
 * <p>
 * This interface provides a clean, testable way to interact with external processes,
 * decoupling the application logic from the specifics of process execution.
 */
public interface MkvVideoProcessor {

    /**
     * Asynchronously executes an MKV command-line tool.
     * <p>
     * The execution is managed in a separate thread, and this method returns immediately
     * with a {@link CompletableFuture} that will be completed when the process finishes.
     *
     * @param command The command and its arguments to execute (e.g., ["mkvmerge", "-o", "out.mkv", "in.mkv"]).
     *                Using a list is crucial to prevent command injection vulnerabilities.
     * @param listener A listener to receive real-time progress updates. Can be null if no progress
     *                 reporting is needed.
     */
    CompletableFuture<String> executeMkv(List<String> command, ProgressListener listener);
}