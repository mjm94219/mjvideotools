package org.mohansworld.videotools.application;

import org.mohansworld.videotools.domain.VideoFormat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A service class responsible for changing the container format of a video file.
 *
 * <p>This class acts as a "remuxer". It copies the existing video, audio, and subtitle
 * streams from an input file into a new container format (e.g., from MKV to MP4)
 * without re-encoding them. This process is very fast as it avoids CPU-intensive
 * video and audio transcoding.</p>
 *
 * <p>It relies on an external {@link FfVideoProcessor} to execute the underlying
 * ffmpeg command.</p>
 */
public class VideoConverter {

    private final FfVideoProcessor videoProcessor;

    /**
     * Constructs a VideoConverter with a dependency on a video processor.
     *
     * @param videoProcessor The processor that will execute the ffmpeg command. Must not be null.
     */
    public VideoConverter(FfVideoProcessor videoProcessor) {
        // Using Objects.requireNonNull is a good practice for constructor validation.
        this.videoProcessor = Objects.requireNonNull(videoProcessor, "FfVideoProcessor cannot be null");
    }

    /**
     * Executes the video remuxing process.
     *
     * @param inputVideoPath The path to the source video file.
     * @param targetFormat   The desired output video format.
     * @param listener       A listener to receive progress updates.
     */
    public void execute(String inputVideoPath, VideoFormat targetFormat, ProgressListener listener) {
        Path inputPath = Paths.get(inputVideoPath);
        Path outputPath = deriveOutputPath(inputPath, targetFormat);

        List<String> command = buildFfmpegCommand(inputPath, outputPath, targetFormat);

        videoProcessor.executeFfmpeg(command, listener);
    }

    /**
     * Constructs the ffmpeg command as a list of arguments.
     *
     * @param inputPath    The path to the input video file.
     * @param outputPath   The path for the generated output file.
     * @param targetFormat The target video format, used to determine subtitle handling.
     * @return A list of strings representing the complete ffmpeg command.
     */
    private List<String> buildFfmpegCommand(Path inputPath, Path outputPath, VideoFormat targetFormat) {
        List<String> command = new ArrayList<>();

        // Path to the ffmpeg executable.
        command.add(FileUtils.getToolPath(FileUtils.Tool.FFMPEG));

        // -y: Overwrite output file if it exists, without asking.
        command.add("-y");

        // -i: Specifies the input file.
        command.add("-i");
        command.add(inputPath.toString());

        // -map 0: Selects all streams (video, audio, subtitles, etc.) from the first input file (input 0).
        command.add("-map");
        command.add("0");

        // -c:v copy: Copies the video stream without re-encoding.
        command.add("-c:v");
        command.add("copy");

        // -c:a copy: Copies all audio streams without re-encoding.
        command.add("-c:a");
        command.add("copy");

        // Add format-specific commands, primarily for handling subtitles.
        // For example, MP4 containers have stricter requirements for subtitle formats (mov_text)
        // than MKV containers (which can hold almost anything, like SRT).
        command.addAll(getSubtitleCommand(targetFormat));

        // The final argument is the output file path.
        command.add(outputPath.toString());

        return command;
    }

    /**
     * Derives a standardized output path from an input path and target format.
     * The output filename will be in the format "basename-converted.ext".
     *
     * @param inputPath    The path of the source video file.
     * @param targetFormat The desired output video format.
     * @return The calculated {@link Path} for the output file.
     */
    private Path deriveOutputPath(Path inputPath, VideoFormat targetFormat) {
        String originalFileName = inputPath.getFileName().toString();
        String baseName = FileUtils.getFilenameWithoutExtension(originalFileName);

        String outputFileName = String.format("%s-converted.%s", baseName, targetFormat.getExtension());

        // Resolve the new filename against the parent directory of the input file.
        return inputPath.getParent().resolve(outputFileName);
    }

    /**
     * Generates the command-line arguments for setting the subtitle codec based on the target video format.
     *
     * @param videoFormat The target {@link VideoFormat}.
     * @return An immutable list of command arguments. Returns an empty list if no specific action is needed.
     */
    private List<String> getSubtitleCommand(VideoFormat videoFormat) {
        return switch (videoFormat) {
            // For MP4/MOV, subtitles must be converted to the mov_text format.
            case MP4, MOV -> List.of("-c:s", "mov_text");
            // For MKV, existing subtitles can be directly copied.
            case MKV -> List.of("-c:s", "copy");
        };
    }

}