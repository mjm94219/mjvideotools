package org.mohansworld.videotools.application;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Extracts subtitle tracks from a video file using FFmpeg and FFprobe.
 * <p>
 * This class first uses ffprobe to inspect the video file and identify all available
 * subtitle streams. Then, for each discovered subtitle stream, it invokes ffmpeg
 * to extract it into a separate .srt file. The output files are named based on the
 * input video's name and the subtitle language (e.g., "myvideo-eng.srt").
 */
public class SubtitleExtractor {

    // --- Constants for JSON keys and command-line arguments to improve readability and prevent typos ---
    private static final String JSON_KEY_STREAMS = "streams";
    private static final String JSON_KEY_CODEC_TYPE = "codec_type";
    private static final String JSON_KEY_INDEX = "index";
    private static final String JSON_KEY_TAGS = "tags";
    private static final String JSON_KEY_LANGUAGE = "language";
    private static final String CODEC_TYPE_SUBTITLE = "subtitle";
    private static final String DEFAULT_LANGUAGE_TAG = "und"; // "undetermined"

    private final FfVideoProcessor videoProcessor;

    /**
     * Constructs a SubtitleExtractor with a dependency on an FfVideoProcessor.
     *
     * @param videoProcessor The processor responsible for executing FFmpeg/FFprobe commands.
     */
    public SubtitleExtractor(FfVideoProcessor videoProcessor) {
        this.videoProcessor = videoProcessor;
    }

    /**
     * Executes the subtitle extraction process for a given video file.
     *
     * @param inputVideoFile The absolute path to the input video file.
     * @param listener       A listener to report progress, completion, or errors.
     */
    public void execute(String inputVideoFile, ProgressListener listener) {
        probeForSubtitles(inputVideoFile, listener)
                .thenAccept(subtitleTracks -> {
                    if (subtitleTracks.isEmpty()) {
                        listener.onComplete("No subtitle tracks found in the video.");
                        return;
                    }

                    List<CompletableFuture<Void>> extractionFutures = subtitleTracks.stream()
                            .map(track -> extractSubtitleTrack(inputVideoFile, track, listener))
                            .toList();

                    CompletableFuture.allOf(extractionFutures.toArray(new CompletableFuture[0]))
                            .thenRun(() -> listener.onComplete("Subtitle extraction complete for all tracks."))
                            .exceptionally(ex -> {
                                listener.onError("An error occurred during subtitle extraction: " + ex.getMessage());
                                return null;
                            });
                })
                .exceptionally(ex -> {
                    listener.onError("Error processing video streams: " + ex.getMessage());
                    return null;
                });
    }

    /**
     * A simple data holder for discovered subtitle track information.
     */
    private record SubtitleTrack(int streamIndex, String language) {}

    /**
     * Runs ffprobe to find all subtitle streams in the video file.
     *
     * @return A CompletableFuture that will complete with a list of discovered SubtitleTrack objects.
     */
    private CompletableFuture<List<SubtitleTrack>> probeForSubtitles(String inputVideoFile, ProgressListener listener) {
        List<String> command = new ArrayList<>();
        command.add(FileUtils.getToolPath(FileUtils.Tool.FFPROBE));
        command.add("-v");
        command.add("quiet");
        command.add("-print_format");
        command.add("json");
        command.add("-show_streams");
        command.add(inputVideoFile);
        return videoProcessor.executeFfprobe(command, listener)
                .thenApply(this::parseSubtitleTracksFromJson);
    }

    /**
     * Parses the JSON output from ffprobe to find subtitle streams.
     *
     * @param jsonOutput The JSON string from ffprobe.
     * @return A list of SubtitleTrack objects.
     * @throws JSONException if the JSON is malformed or required keys are missing.
     */
    private List<SubtitleTrack> parseSubtitleTracksFromJson(String jsonOutput) {
        if (jsonOutput == null || jsonOutput.isEmpty()) {
            throw new IllegalStateException("ffprobe returned empty output.");
        }

        JSONObject root = new JSONObject(jsonOutput);
        JSONArray streams = root.getJSONArray(JSON_KEY_STREAMS);

        return IntStream.range(0, streams.length())
                .mapToObj(streams::getJSONObject)
                .filter(stream -> CODEC_TYPE_SUBTITLE.equals(stream.optString(JSON_KEY_CODEC_TYPE)))
                .map(stream -> {
                    int index = stream.getInt(JSON_KEY_INDEX);
                    String lang = Optional.ofNullable(stream.optJSONObject(JSON_KEY_TAGS))
                            .map(tags -> tags.optString(JSON_KEY_LANGUAGE, DEFAULT_LANGUAGE_TAG))
                            .orElse(DEFAULT_LANGUAGE_TAG);
                    return new SubtitleTrack(index, lang);
                })
                .collect(Collectors.toList());
    }

    /**
     * Executes an ffmpeg command to extract a single subtitle track.
     *
     * @param inputVideoFile The input video file path.
     * @param track          The SubtitleTrack object containing stream index and language.
     * @param listener       The progress listener.
     * @return A CompletableFuture that completes when the ffmpeg process finishes.
     */
    private CompletableFuture<Void> extractSubtitleTrack(String inputVideoFile, SubtitleTrack track, ProgressListener listener) {
        Path inputPath = Paths.get(inputVideoFile);
        Path outputPath = createOutputFilePath(inputPath, track.language());

        List<String> command = new ArrayList<>();
        command.add(FileUtils.getToolPath(FileUtils.Tool.FFMPEG));
        command.add("-y");
        command.add("-i");
        command.add(inputVideoFile);
        command.add("-map");
        command.add("0:" + track.streamIndex());
        command.add("-c:s");
        command.add("srt");
        command.add(outputPath.toString());

        // This assumes executeFfmpeg returns a CompletableFuture<Integer> with the process exit code.
        // We chain it to check the exit code and convert the result to CompletableFuture<Void>.
        return videoProcessor.executeFfmpeg(command, listener)
                .thenAccept(exitCode -> {
                    // If the ffmpeg process returned a non-zero exit code, it indicates an error.
                    // We throw an exception here to propagate the failure down the CompletableFuture chain.
                    // This will be caught by the .exceptionally() block in the execute() method.
                    if (exitCode != 0) {
                        throw new RuntimeException(String.format(
                                "FFmpeg process for track %d ('%s') failed with exit code: %d",
                                track.streamIndex(), track.language(), exitCode
                        ));
                    }
                    // If exitCode is 0, this consumer completes successfully, and the resulting
                    // CompletableFuture<Void> also completes successfully.
                });
    }

    /**
     * Creates a unique output file path for a subtitle track.
     * Example: /path/to/video.mkv -> /path/to/video-eng.srt
     *
     * @param inputPath The path to the original video file.
     * @param language  The language code for the subtitle track.
     * @return The calculated output Path.
     */
    private Path createOutputFilePath(Path inputPath, String language) {
        String originalFileName = inputPath.getFileName().toString();
        String baseName = FileUtils.getFilenameWithoutExtension(originalFileName);
        String outputFileName = String.format("%s-%s.srt", baseName, language);
        return inputPath.resolveSibling(outputFileName);
    }
}