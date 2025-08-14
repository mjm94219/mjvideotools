package org.mohansworld.videotools.domain;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents common audio file formats, providing type-safe constants.
 * <p>
 * Each format encapsulates its standard file extension and a human-readable description.
 * This enum includes a utility method to look up a format by its extension.
 */
public enum AudioFormat {
    AAC("aac", "Advanced Audio Coding"),
    MP3("mp3", "MPEG Audio Layer III"),
    WAV("wav", "Waveform Audio File Format");

    // A static map for efficient, case-insensitive lookup of formats by extension.
    // This is initialized once when the class is loaded, avoiding repeated iteration.
    private static final Map<String, AudioFormat> EXTENSION_MAP =
            Stream.of(values())
                    .collect(Collectors.toUnmodifiableMap(
                            format -> format.getExtension().toLowerCase(), // Key: lowercase extension
                            Function.identity()                          // Value: the enum constant itself
                    ));

    /** The common file extension for the audio format (e.g., "mp3"), in lowercase. */
    private final String extension;

    /** A human-readable description of the audio format. */
    private final String description;

    /**
     * Constructs an AudioFormat enum constant.
     *
     * @param extension   The file extension.
     * @param description The full description of the format.
     */
    AudioFormat(String extension, String description) {
        this.extension = extension;
        this.description = description;
    }

    /**
     * Gets the file extension associated with the format.
     *
     * @return The file extension as a lowercase string (e.g., "mp3").
     */
    public String getExtension() {
        return extension;
    }

    /**
     * Gets the human-readable description of the format.
     *
     * @return The description string (e.g., "MPEG Audio Layer III").
     */
    public String getDescription() {
        return description;
    }

    /**
     * Finds an {@code AudioFormat} from a given file extension string.
     * <p>
     * This lookup is case-insensitive. For example, "mp3", "MP3", and "Mp3" will all
     * correctly return {@code AudioFormat.MP3}.
     *
     * @param extension The file extension to look up (e.g., "aac"). Can be null or blank.
     * @return an {@link Optional} containing the matching {@code AudioFormat},
     *         or {@link Optional#empty()} if no match is found.
     */
    public static Optional<AudioFormat> fromExtension(String extension) {
        if (extension == null || extension.trim().isEmpty()) {
            return Optional.empty();
        }
        // Use the pre-populated map for an efficient O(1) lookup.
        return Optional.ofNullable(EXTENSION_MAP.get(extension.toLowerCase()));
    }

    /**
     * Returns a user-friendly string representation of the format.
     *
     * @return A string in the format "NAME (Description)", e.g., "MP3 (MPEG Audio Layer III)".
     */
    @Override
    public String toString() {
        return this.name() + " (" + description + ")";
    }
}