package org.mohansworld.videotools.application;

import org.mohansworld.videotools.domain.MkvPropertyInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A service class responsible for reading and writing properties of MKV video files
 * by orchestrating calls to the mkvmerge command-line tool.
 * It operates asynchronously, returning CompletableFuture for non-blocking execution.
 */
public class MkvPropertyEditor {

    // --- Constants for mkvmerge command-line arguments ---
    private static final String EDIT_FLAG = "--edit";
    private static final String SET_FLAG = "--set";
    private static final String JSON_IDENTIFICATION_FLAG = "-J";
    private static final String GLOBAL_INFO_SELECTOR = "info";
    private static final String TRACK_SELECTOR_PREFIX = "track:";
    private static final String TITLE_PROPERTY = "title=";
    private static final String NAME_PROPERTY = "name=";
    private static final String ENABLED_FLAG_PROPERTY = "flag-enabled=";
    private static final String DEFAULT_FLAG_PROPERTY = "flag-default=";

    private final MkvVideoProcessor videoProcessor;

    /**
     * Constructs an MkvPropertyEditor with a dependency on a video processor.
     *
     * @param videoProcessor The processor responsible for executing external commands.
     */
    public MkvPropertyEditor(final MkvVideoProcessor videoProcessor) {
        this.videoProcessor = videoProcessor;
    }

    /**
     * A simple, immutable data carrier for track update information.
     * Using a record improves type safety and readability over a Map<String, String>.
     *
     * @param selector The track selector (e.g., "v1", "a1", "s1").
     * @param name The new name for the track.
     * @param enabled The new state of the 'enabled' flag ("1" for true, "0" for false).
     * @param isDefault The new state of the 'default' flag ("1" for true, "0" for false).
     */
    public record TrackUpdateInfo(String selector, String name, String enabled, String isDefault) {}

    /**
     * Asynchronously retrieves properties of an MKV file in JSON format.
     * It uses the 'mkvmerge -J <filePath>' command.
     *
     * @param filePath The absolute path to the MKV file.
     * @param progressListener A listener to report progress of the command execution.
     * @return A CompletableFuture that, upon completion, will contain the MkvPropertyInfo.
     */
    public CompletableFuture<MkvPropertyInfo> getProperties(final String filePath, final ProgressListener progressListener) {
        final List<String> command = new ArrayList<>();
        command.add(FileUtils.getToolPath(FileUtils.Tool.MKVMERGE));
        command.add(JSON_IDENTIFICATION_FLAG); // Asks mkvmerge to output properties as JSON
        command.add(filePath);

        // Execute the command and then parse the JSON output into an MkvPropertyInfo object.
        // --- CHANGE: Use the static factory method instead of the private constructor ---
        return videoProcessor.executeMkv(command, progressListener)
                .thenApply(MkvPropertyInfo::fromJson);
    }

    /**
     * Asynchronously updates properties of an MKV file, including the global title and individual track properties.
     *
     * @param filePath The absolute path to the MKV file to be modified.
     * @param newTitle The new global title for the video file.
     * @param trackUpdates A list of TrackUpdateInfo objects, each containing the data for a track to be updated.
     * @param progressListener A listener to report progress of the command execution.
     * @return A CompletableFuture<Void> that completes when the update operation is finished.
     */
    public CompletableFuture<Void> updateProperties(final String filePath, final String newTitle, final List<TrackUpdateInfo> trackUpdates, final ProgressListener progressListener) {
        final List<String> command = buildUpdatePropertiesCommand(filePath, newTitle, trackUpdates);
        // Execute the command and ignore the output, signaling completion when done.
        return videoProcessor.executeMkv(command, progressListener).thenAccept(output -> {});
    }

    /**
     * Builds the command-line arguments for mkvmerge to update file properties.
     *
     * @param filePath The target MKV file.
     * @param newTitle The new global title.
     * @param trackUpdates The list of track modifications.
     * @return A list of strings representing the complete command and its arguments.
     */
    private List<String> buildUpdatePropertiesCommand(final String filePath, final String newTitle, final List<TrackUpdateInfo> trackUpdates) {
        final List<String> command = new ArrayList<>();
        command.add(FileUtils.getToolPath(FileUtils.Tool.MKVPROPEDIT));
        command.add(filePath);

        // Part 1: Edit global container properties (e.g., the main title).
        command.add(EDIT_FLAG);
        command.add(GLOBAL_INFO_SELECTOR); // Target the global 'info' element.
        command.add(SET_FLAG);
        command.add(TITLE_PROPERTY + newTitle);

        // Part 2: Edit properties for each specified track.
        for (final TrackUpdateInfo track : trackUpdates) {
            command.add(EDIT_FLAG);
            command.add(TRACK_SELECTOR_PREFIX + track.selector()); // Target a specific track (e.g., track:v1)

            command.add(SET_FLAG);
            command.add(NAME_PROPERTY + track.name()); // Set the track name

            command.add(SET_FLAG);
            command.add(ENABLED_FLAG_PROPERTY + track.enabled()); // Set the enabled flag

            command.add(SET_FLAG);
            command.add(DEFAULT_FLAG_PROPERTY + track.isDefault()); // Set the default track flag
        }
        return command;
    }
}