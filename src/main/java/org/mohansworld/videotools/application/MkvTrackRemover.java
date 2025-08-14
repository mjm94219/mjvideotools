package org.mohansworld.videotools.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * A service for removing video, audio, or subtitle tracks from an MKV file using mkvmerge.
 * <p>
 * This class constructs the necessary mkvmerge command and delegates its execution
 * to an MkvVideoProcessor. The logic is based on specifying which tracks to *keep*.
 */
public class MkvTrackRemover {

    // --- mkvmerge Command Line Flags ---
    private static final String FLAG_OUTPUT_FILE = "-o";
    private static final String FLAG_VIDEO_TRACKS = "-d";
    private static final String FLAG_AUDIO_TRACKS = "-a";
    private static final String FLAG_SUBTITLE_TRACKS = "-s";
    private static final String FLAG_NO_VIDEO = "--no-video";
    private static final String FLAG_NO_AUDIO = "--no-audio";
    private static final String FLAG_NO_SUBTITLES = "--no-subtitles";
    private static final String FLAG_NO_ATTACHMENTS = "--no-attachments";
    private static final String TRACK_ID_SEPARATOR = ",";

    private final MkvVideoProcessor videoProcessor;

    /**
     * Constructs an MkvTrackRemover.
     *
     * @param videoProcessor The processor responsible for executing external commands.
     */
    public MkvTrackRemover(MkvVideoProcessor videoProcessor) {
        this.videoProcessor = Objects.requireNonNull(videoProcessor, "videoProcessor cannot be null");
    }

    /**
     * Asynchronously removes tracks from an input MKV file and saves it to an output file.
     * <p>
     * The removal logic is based on specifying which track IDs to keep. Any tracks not
     * included in the 'keep' lists will be removed from the output file.
     *
     * @param options The configuration for the track removal operation.
     * @param progressListener A listener to receive progress updates.
     * @return a CompletableFuture<Void> that completes when the process is finished.
     */
    public CompletableFuture<Void> removeTracks(TrackRemovalOptions options, ProgressListener progressListener) {
        List<String> command = buildRemoveTracksCommand(options);
        // The final thenAccept(r -> {}) transforms the future's result type to Void,
        // signaling completion without a return value.
        return videoProcessor.executeMkv(command, progressListener).thenAccept(result -> {});
    }

    /**
     * Builds the mkvmerge command line arguments list based on the provided options.
     *
     * @param options The track removal options.
     * @return A list of strings representing the command and its arguments.
     */
    private List<String> buildRemoveTracksCommand(TrackRemovalOptions options) {
        List<String> command = new ArrayList<>();
        command.add(FileUtils.getToolPath(FileUtils.Tool.MKVMERGE));
        command.add(FLAG_OUTPUT_FILE);
        command.add(options.outputFile());

        addTrackSelection(command, options.videoTrackIdsToKeep(), FLAG_VIDEO_TRACKS, FLAG_NO_VIDEO);
        addTrackSelection(command, options.audioTrackIdsToKeep(), FLAG_AUDIO_TRACKS, FLAG_NO_AUDIO);
        addTrackSelection(command, options.subtitleTrackIdsToKeep(), FLAG_SUBTITLE_TRACKS, FLAG_NO_SUBTITLES);

        command.add(FLAG_NO_ATTACHMENTS); // By design, always remove attachments
        command.add(options.inputFile());

        return command;
    }

    /**
     * A helper method to add track selection arguments to the command list.
     * If the list of IDs to keep is empty, it adds the flag to remove all tracks of that type.
     * Otherwise, it adds the flag to specify which tracks to keep.
     *
     * @param command The command list to modify.
     * @param trackIdsToKeep The list of track IDs to keep.
     * @param keepFlag The mkvmerge flag for specifying tracks to keep (e.g., "-d").
     * @param removeAllFlag The mkvmerge flag for removing all tracks of a type (e.g., "--no-video").
     */
    private void addTrackSelection(List<String> command, List<Integer> trackIdsToKeep, String keepFlag, String removeAllFlag) {
        if (trackIdsToKeep == null || trackIdsToKeep.isEmpty()) {
            command.add(removeAllFlag);
        } else {
            command.add(keepFlag);
            command.add(
                    trackIdsToKeep.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(TRACK_ID_SEPARATOR))
            );
        }
    }

    /**
     * A parameter object to hold all options for a track removal operation.
     * Using a record provides immutability, a constructor, getters, equals, hashCode, and toString for free.
     */
    public record TrackRemovalOptions(
            String inputFile,
            String outputFile,
            List<Integer> videoTrackIdsToKeep,
            List<Integer> audioTrackIdsToKeep,
            List<Integer> subtitleTrackIdsToKeep
    ) {
        public TrackRemovalOptions {
            // Add validation for constructor parameters
            Objects.requireNonNull(inputFile, "Input file cannot be null.");
            Objects.requireNonNull(outputFile, "Output file cannot be null.");
            // Defensive copies to ensure the lists inside the record are immutable from the outside
            videoTrackIdsToKeep = List.copyOf(videoTrackIdsToKeep != null ? videoTrackIdsToKeep : List.of());
            audioTrackIdsToKeep = List.copyOf(audioTrackIdsToKeep != null ? audioTrackIdsToKeep : List.of());
            subtitleTrackIdsToKeep = List.copyOf(subtitleTrackIdsToKeep != null ? subtitleTrackIdsToKeep : List.of());
        }
    }
}