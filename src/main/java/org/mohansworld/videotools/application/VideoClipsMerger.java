package org.mohansworld.videotools.application;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Merges multiple video clips into a single output file using FFmpeg's concat demuxer.
 * This method is highly efficient as it avoids re-encoding the video streams ("-c copy"),
 * but it requires that all input clips have the same codecs and parameters.
 */
public class VideoClipsMerger {
    private final FfVideoProcessor videoProcessor;

    /**
     * Constructs a VideoClipsMerger.
     *
     * @param videoProcessor The processor responsible for executing FFmpeg commands.
     */
    public VideoClipsMerger(FfVideoProcessor videoProcessor) {
        this.videoProcessor = videoProcessor;
    }

    /**
     * Executes the merge operation for a list of video clips.
     * <p>
     * This method works by creating a temporary text file that lists all the clips to be merged,
     * then passes this file to FFmpeg. The temporary file is cleaned up automatically
     * after the FFmpeg process completes.
     *
     * @param clipsToMerge A list of absolute paths to the video clips to merge.
     * @param outputFile   The path for the final merged video file.
     * @param listener     A listener to receive progress updates and error notifications.
     */
    public void execute(List<String> clipsToMerge, String outputFile, ProgressListener listener) {
        if (clipsToMerge == null || clipsToMerge.isEmpty()) {
            listener.onError("No video clips provided to merge.");
            return;
        }

        final Path listFilePath;
        try {
            // Step 1: Create a temporary file listing all clips to merge.
            listFilePath = createMergeListFile(clipsToMerge);
        } catch (IOException e) {
            listener.onError("Failed to create temporary file for merging: " + e.getMessage());
            // If we fail here, there's nothing to clean up, so we can just return.
            return;
        }

        // Step 2: Build the FFmpeg command.
        List<String> command = buildFfmpegCommand(listFilePath, outputFile);

        // Step 3: Execute the command and set up cleanup for when it completes.
        videoProcessor.executeFfmpeg(command, listener)
                .whenComplete((status, ex) -> {
                    // Step 4: (Cleanup) Delete the temporary list file after FFmpeg is done.
                    // This runs whether the process succeeded or failed.
                    try {
                        Files.deleteIfExists(listFilePath);
                    } catch (IOException e) {
                        // This cleanup error is secondary. The main operation is complete.
                        // Report it, but it's less critical than a merge failure.
                        listener.onError("Cleanup failed: Could not delete temporary file " + listFilePath + ". Error: " + e.getMessage());
                    }
                });
    }

    /**
     * Creates a temporary text file listing the input files for FFmpeg's concat demuxer.
     *
     * @param filePaths The list of video clip paths.
     * @return The path to the newly created temporary list file.
     * @throws IOException if the file cannot be created or written to.
     */
    private Path createMergeListFile(List<String> filePaths) throws IOException {
        Path listFile = Files.createTempFile("ffmpeg-merge-list", ".txt");

        // Use try-with-resources to ensure the writer is closed automatically.
        // Files.newBufferedWriter is a modern and preferred way to get a writer for a Path.
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(listFile, StandardCharsets.UTF_8))) {
            for (String path : filePaths) {
                // FFmpeg's concat format requires 'file' followed by the path in single quotes.
                // Any single quotes within the path itself must be escaped.
                // The correct escape sequence is replacing ' with '\''.
                String escapedPath = path.replace("'", "'\\''");
                writer.println("file '" + escapedPath + "'");
            }
        }
        return listFile;
    }

    /**
     * Builds the FFmpeg command list for the concat operation.
     *
     * @param listFilePath The path to the temporary file containing the list of clips.
     * @param outputFile   The path for the final merged video.
     * @return A list of strings representing the command and its arguments.
     */
    private List<String> buildFfmpegCommand(Path listFilePath, String outputFile) {
        List<String> command = new ArrayList<>();
        command.add(FileUtils.getToolPath(FileUtils.Tool.FFMPEG));
        command.add("-f");
        command.add("concat"); // Use the concat demuxer.
        command.add("-safe");
        command.add("0");      // Allow unsafe file paths (required for absolute paths).
        command.add("-i");
        command.add(listFilePath.toString()); // Input is the list file.
        command.add("-c");
        command.add("copy");   // Copy streams without re-encoding for speed and quality.
        command.add(outputFile);
        return command;
    }
}