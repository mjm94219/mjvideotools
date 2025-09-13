package org.mohansworld.videotools.domain;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a set of supported video formats.
 * Each format includes a file extension and a technical description.
 * This enum is designed to be easily extensible with new formats.
 */
public enum VideoFormat {
    MP4("mp4", "MPEG-4 Part 14"),
    MKV("mkv", "Matroska Multimedia Container"),
    MOV("mov", "QuickTime File Format");

    private final String extension;
    private final String description;

    /**
     * A static map for efficient, case-insensitive lookup of formats by their extension.
     * This avoids iterating over the values array on every lookup.
     */
    private static final Map<String, VideoFormat> EXTENSION_MAP = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(
                    format -> format.getExtension().toLowerCase(), // Key: lowercase extension
                    Function.identity() // Value: the enum constant itself
            ));

    /**
     * Constructs a VideoFormat instance.
     *
     * @param extension The file extension (e.g., "mp4").
     * @param description A brief description of the format.
     */
    VideoFormat(String extension, String description) {
        this.extension = extension;
        this.description = description;
    }

    /**
     * Returns the file extension associated with the video format.
     *
     * @return The file extension as a lowercase string (e.g., "mp4").
     */
    public String getExtension() {
        return extension;
    }

    /**
     * Returns the technical description of the video format.
     *
     * @return The description string (e.g., "MPEG-4 Part 14").
     */
    public String getDescription() {
        return description;
    }

    /**
     * Provides a human-readable representation of the video format.
     *
     * @return A string in the format "NAME (Description)", e.g., "MP4 (MPEG-4 Part 14)".
     */
    @Override
    public String toString() {
        return String.format("%s (%s)", this.name(), this.description);
    }

    /**
     * Finds a VideoFormat by its file extension in a case-insensitive manner.
     *
     * @param extension The file extension to look up (e.g., "mkv" or "MKV").
     * @return The corresponding VideoFormat instance.
     * @throws IllegalArgumentException if the extension is null or no matching format is found.
     */
    public static VideoFormat fromExtension(String extension) {
        if (extension == null) {
            throw new IllegalArgumentException("Extension cannot be null.");
        }
        VideoFormat format = EXTENSION_MAP.get(extension.toLowerCase());
        if (format == null) {
            throw new IllegalArgumentException("No video format found for extension: " + extension);
        }
        return format;
    }
}