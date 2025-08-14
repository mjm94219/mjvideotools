package org.mohansworld.videotools.application;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Splits a video file into multiple segments of a specified duration using ffmpeg.
 * <p>
 * This class constructs and executes an ffmpeg command to perform a lossless split
 * by stream copying the contents into new segment files.
 */
public final class VideoSplitter {
    private final FfVideoProcessor videoProcessor;

    /**
     * Constructs a VideoSplitter with a dependency on a video processor.
     *
     * @param videoProcessor The processor responsible for executing ffmpeg commands. Must not be null.
     */
    public VideoSplitter(FfVideoProcessor videoProcessor) {
        this.videoProcessor = Objects.requireNonNull(videoProcessor, "FfVideoProcessor cannot be null.");
    }

    /**
     * Executes the video splitting process.
     *
     * @param inputVideoPath The path to the source video file.
     * @param segmentTime    The duration of each segment in a format accepted by ffmpeg (e.g., "HH:MM:SS" or seconds).
     * @param listener       A listener to receive progress updates during the operation.
     */
    public void execute(String inputVideoPath, String segmentTime, ProgressListener listener) {
        Path inputPath = Paths.get(inputVideoPath);
        String outputPattern = createOutputPattern(inputPath);
        Path outputDirectory = inputPath.getParent();

        // If the input path has no parent (e.g., "video.mp4"), output to the current working directory.
        Path fullOutputPatternPath = (outputDirectory != null)
                ? outputDirectory.resolve(outputPattern)
                : Paths.get(outputPattern);

        List<String> command = buildFfmpegCommand(inputVideoPath, segmentTime, fullOutputPatternPath.toString());
        videoProcessor.executeFfmpeg(command, listener);
    }

    /**
     * Creates the output filename pattern for the ffmpeg segment muxer.
     * Example: "my-video.mp4" -> "my-video-0001.mp4, my-video-0002.mp4" and so on.
     *
     * @param inputPath The path of the input video file.
     * @return A string representing the output file pattern.
     */
    private String createOutputPattern(Path inputPath) {
        String filename = inputPath.getFileName().toString();
        String baseName = FileUtils.getFilenameWithoutExtension(filename);
        String extension = FileUtils.getFileExtension(filename);
        return String.format("%s-%%04d.%s", baseName, extension);
    }

    /**
     * Builds the complete ffmpeg command as a list of arguments.
     *
     * @param inputVideoPath  Path to the input video.
     * @param segmentTime     Duration for each segment.
     * @param outputPattern   The output file pattern for ffmpeg.
     * @return A list of strings representing the command and its arguments.
     */
    private List<String> buildFfmpegCommand(String inputVideoPath, String segmentTime, String outputPattern) {
        List<String> command = new ArrayList<>();
        command.add(FileUtils.getToolPath(FileUtils.Tool.FFMPEG));
        command.add("-y"); // Overwrite output files without asking
        command.add("-i");
        command.add(inputVideoPath);
        command.add("-c");
        command.add("copy"); // Use stream copy for speed, no re-encoding
        command.add("-map");
        command.add("0"); // Map all streams from input 0
        command.add("-segment_time");
        command.add(segmentTime);
        command.add("-f");
        command.add("segment"); // Use the segment muxer
        command.add("-reset_timestamps");
        command.add("1"); // Reset timestamps for each segment to make them playable
        command.add(outputPattern);
        return command;
    }
}