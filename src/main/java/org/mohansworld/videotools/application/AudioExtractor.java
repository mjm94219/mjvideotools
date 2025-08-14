package org.mohansworld.videotools.application;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mohansworld.videotools.domain.AudioFormat;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extracts audio tracks from a video file using FFmpeg and ffprobe.
 * <p>
 * This class first uses ffprobe to identify all audio streams in the input video file.
 * Then, for each audio stream found, it invokes FFmpeg to extract it into a separate
 * audio file in the specified format. The process is asynchronous and reports progress,
 * completion, and errors through a {@link ProgressListener}.
 */
public class AudioExtractor {

    private final FfVideoProcessor videoProcessor;

    /**
     * Constructs an AudioExtractor with a dependency on an {@link FfVideoProcessor}.
     *
     * @param videoProcessor The processor responsible for executing FFmpeg/ffprobe commands.
     */
    public AudioExtractor(FfVideoProcessor videoProcessor) {
        this.videoProcessor = videoProcessor;
    }

    /**
     * Executes the audio extraction process.
     * <p>
     * This method is asynchronous. It initiates the process and returns immediately.
     * The results of the operation are communicated through the provided listener.
     *
     * @param inputVideoFile The absolute path to the input video file.
     * @param audioFormat    The target audio format for the extracted files (e.g., MP3, AAC).
     * @param listener       The listener to receive progress updates, completion notifications, and error messages.
     */
    public void execute(String inputVideoFile, AudioFormat audioFormat, ProgressListener listener) {
        // Build and execute the ffprobe command to get stream information as JSON.
        List<String> ffprobeCommand = buildFfprobeCommand(inputVideoFile);

        // The process is handled asynchronously using CompletableFuture.
        videoProcessor.executeFfprobe(ffprobeCommand, listener)
                .thenAccept(jsonOutput -> {
                    // This block is executed upon successful completion of ffprobe.
                    processFfprobeOutput(jsonOutput, inputVideoFile, audioFormat, listener);
                })
                .exceptionally(ex -> {
                    // This block handles any exceptions thrown during the async execution.
                    listener.onError("Error during ffprobe execution: " + ex.getMessage());
                    return null; // Return null to complete the exceptionally stage.
                });
    }

    /**
     * Parses the JSON output from ffprobe and initiates extraction for each audio stream.
     *
     * @param jsonOutput     The JSON string returned by ffprobe.
     * @param inputVideoFile The path to the video file, needed for extraction.
     * @param audioFormat    The target audio format.
     * @param listener       The listener for reporting outcomes.
     */
    private void processFfprobeOutput(String jsonOutput, String inputVideoFile, AudioFormat audioFormat, ProgressListener listener) {
        if (jsonOutput == null || jsonOutput.isEmpty()) {
            listener.onError("Failed to get stream information: ffprobe returned empty output.");
            return;
        }

        try {
            JSONObject root = new JSONObject(jsonOutput);
            JSONArray streams = root.getJSONArray("streams");

            int audioTrackIndex = 0; // 0-based index for audio-only streams (e.g., 0:a:0, 0:a:1)
            for (int i = 0; i < streams.length(); i++) {
                JSONObject stream = streams.getJSONObject(i);
                // Check if the current stream is an audio stream.
                if ("audio".equals(stream.optString("codec_type"))) {
                    // Initiate extraction for this specific audio track.
                    extractAudioTrack(inputVideoFile, audioTrackIndex, audioFormat, listener);
                    audioTrackIndex++;
                }
            }

            if (audioTrackIndex == 0) {
                // Report completion with a message if no audio was found.
                // This is considered a successful completion of the operation, not an error.
                listener.onComplete("No audio tracks found in the video.");
            }
        } catch (JSONException e) {
            listener.onError("Failed to parse ffprobe JSON output: " + e.getMessage());
        }
    }

    /**
     * Extracts a single audio track using FFmpeg.
     *
     * @param inputVideoFile  The source video file.
     * @param audioTrackIndex The 0-based index of the audio track to extract (e.g., 0 for the first audio track).
     * @param audioFormat     The target audio format.
     * @param listener        The listener for progress updates on the FFmpeg process.
     */
    private void extractAudioTrack(String inputVideoFile, int audioTrackIndex, AudioFormat audioFormat, ProgressListener listener) {
        // Generate a descriptive output file name.
        String outputFile = generateOutputFilePath(inputVideoFile, audioTrackIndex, audioFormat);

        // Build the FFmpeg command for extraction.
        List<String> ffmpegCommand = buildExtractionCommand(inputVideoFile, outputFile, audioTrackIndex, audioFormat);

        // Execute the FFmpeg command. The videoProcessor will handle reporting progress to the listener.
        videoProcessor.executeFfmpeg(ffmpegCommand, listener);
    }

    /**
     * Builds the ffprobe command to retrieve stream information in JSON format.
     *
     * @param inputVideoFile The path to the input video.
     * @return A list of strings representing the command and its arguments.
     */
    private List<String> buildFfprobeCommand(String inputVideoFile) {
        List<String> command = new ArrayList<>();
        command.add(FileUtils.getToolPath(FileUtils.Tool.FFPROBE));
        command.add("-v");
        command.add("quiet");
        command.add("-print_format");
        command.add("json");
        command.add("-show_streams");
        command.add(inputVideoFile);
        return new ArrayList<>(command);
    }

    /**
     * Builds the FFmpeg command to extract a specific audio track.
     *
     * @param inputVideoFile  The source video file.
     * @param outputFile      The destination audio file.
     * @param audioTrackIndex The index of the audio track to extract.
     * @param audioFormat     The desired output audio format.
     * @return A list of strings representing the command and its arguments.
     */
    private List<String> buildExtractionCommand(String inputVideoFile, String outputFile, int audioTrackIndex, AudioFormat audioFormat) {
        List<String> command = new ArrayList<>();
        command.add(FileUtils.getToolPath(FileUtils.Tool.FFMPEG));
        command.add("-y"); // Overwrite output file if it exists.
        command.add("-i");
        command.add(inputVideoFile);
        command.add("-map");
        // Map the specific audio stream. "0:a:N" means from the first input file (0),
        // select the audio stream (a) at index N.
        command.add("0:a:" + audioTrackIndex);
        // Add format-specific arguments, like bitrate (-b:a) or codec (-c:a).
        command.addAll(getAudioBitrateCommandArgs(audioFormat));
        command.add(outputFile);
        return command;
    }

    /**
     * Generates a path for the output audio file.
     * The name is based on the input file, with an "-audio-N" suffix.
     * Example: "my_video.mp4" -> "my_video-audio-0.mp3"
     *
     * @param inputVideoFile  The path to the original video file.
     * @param audioTrackIndex The index of the audio track.
     * @param audioFormat     The target audio format.
     * @return The generated absolute path for the output file.
     */
    private String generateOutputFilePath(String inputVideoFile, int audioTrackIndex, AudioFormat audioFormat) {
        File inputFile = new File(inputVideoFile);
        String originalName = inputFile.getName();

        // Robustly get the base name (name without extension).
        String baseName = FileUtils.getFilenameWithoutExtension(originalName);

        // Create the new filename.
        String outputFileName = String.format("%s-audio-%d.%s", baseName, audioTrackIndex, audioFormat.getExtension());

        // Construct the full path in the same directory as the input file.
        Path parentDir = Paths.get(inputFile.getParent());
        return parentDir.resolve(outputFileName).toString();
    }

    /**
     * Generates the command-line arguments for setting the audio bitrate based on the target audio format.
     *
     * @param audioFormat The target {@link AudioFormat}.
     * @return An immutable list of command arguments. Returns an empty list if no specific action is needed.
     */
    private List<String> getAudioBitrateCommandArgs(AudioFormat audioFormat) {
        return switch (audioFormat) {
            // For AAC/MP3, set a high-quality bitrate of 320k.
            case AAC, MP3 -> List.of("-b:a", "320k");
            // For any other format, do nothing.
            default -> Collections.emptyList();
        };
    }
}