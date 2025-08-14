package org.mohansworld.videotools.application;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Defines a contract for executing FFmpeg and FFprobe commands asynchronously.
 * <p>
 * This interface abstracts the low-level details of running external processes,
 * providing a clean, modern, and testable API for video and audio manipulation.
 * Implementations are expected to handle process creation, stream management (stdout, stderr),
 * and progress parsing.
 */
public interface FfVideoProcessor {
    /**
     * Asynchronously executes an FFmpeg command with the given arguments.
     * <p>
     * The implementation is responsible for locating the `ffmpeg` executable and prepending it
     * to the arguments list before execution.
     *
     * @param command The list of command-line arguments to pass to FFmpeg (e.g., ["-i", "input.mp4", "output.webm"]).
     * @param listener  An optional listener to receive progress updates. Can be {@code null} if no progress reporting is needed.
     */
    CompletableFuture<Integer> executeFfmpeg(List<String> command, ProgressListener listener);

    /**
     * Asynchronously executes an FFprobe command with the given arguments.
     * <p>
     * The implementation is responsible for locating the `ffprobe` executable and prepending it
     * to the arguments list before execution.
     *
     * @param command The list of command-line arguments to pass to FFprobe (e.g., ["-v", "quiet", "-print_format", "json", "-show_format", "input.mp4"]).
     * @param listener  An optional listener for progress, though FFprobe commands are typically fast. Can be {@code null}.
     */
    CompletableFuture<String> executeFfprobe(List<String> command, ProgressListener listener);
}