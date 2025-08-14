package org.mohansworld.videotools.domain;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents properties of an MKV container, parsed from mkvmerge's JSON output.
 * This class is immutable. Instances should be created via the {@link #fromJson(String)} factory method.
 */
public final class MkvPropertyInfo {

    private static final String KEY_CONTAINER = "container";
    private static final String KEY_PROPERTIES = "properties";
    private static final String KEY_TITLE = "title";
    private static final String KEY_TRACKS = "tracks";
    private static final String KEY_ID = "id";
    private static final String KEY_TYPE = "type";
    private static final String KEY_CODEC = "codec";
    private static final String KEY_TRACK_NAME = "track_name";
    private static final String KEY_DEFAULT_TRACK = "default_track";
    private static final String KEY_ENABLED_TRACK = "enabled_track";
    private static final String KEY_LANGUAGE = "language";

    private final String title;
    private final List<Track> tracks;

    /**
     * Private constructor to enforce object creation via the factory method.
     */
    private MkvPropertyInfo(String title, List<Track> tracks) {
        this.title = title;
        // Store an unmodifiable view of the list to ensure true immutability.
        this.tracks = Collections.unmodifiableList(tracks);
    }

    /**
     * Parses a JSON string (from mkvmerge) and creates an MkvPropertyInfo instance.
     *
     * @param json The JSON string to parse.
     * @return A new, immutable MkvPropertyInfo instance.
     * @throws IllegalArgumentException if the JSON is malformed or missing required fields.
     */
    public static MkvPropertyInfo fromJson(String json) {
        try {
            JSONObject root = new JSONObject(json);
            String title = root.optJSONObject(KEY_CONTAINER)
                    .optJSONObject(KEY_PROPERTIES)
                    .optString(KEY_TITLE, "");

            List<Track> parsedTracks = new ArrayList<>();
            JSONArray tracksArray = root.getJSONArray(KEY_TRACKS);

            int videoCounter = 1;
            int audioCounter = 1;
            int subtitleCounter = 1;

            for (int i = 0; i < tracksArray.length(); i++) {
                JSONObject trackJson = tracksArray.getJSONObject(i);
                TrackType type = TrackType.fromString(trackJson.getString(KEY_TYPE));

                // Generate the track selector used by mkvpropedit (e.g., "v1", "a1", "s1").
                String selector = switch (type) {
                    case VIDEO -> "v" + videoCounter++;
                    case AUDIO -> "a" + audioCounter++;
                    case SUBTITLES -> "s" + subtitleCounter++;
                    case UNKNOWN ->
                        // Fallback for other types. mkvpropedit uses 1-based track IDs for direct access.
                        // The JSON 'id' is 0-based, so we add 1.
                            "@" + (trackJson.getInt(KEY_ID) + 1);
                };
                parsedTracks.add(Track.fromJson(trackJson, selector));
            }
            return new MkvPropertyInfo(title, parsedTracks);
        } catch (JSONException e) {
            // Wrap the original exception to provide more context.
            throw new IllegalArgumentException("Failed to parse MKV properties from JSON", e);
        }
    }

    public String getTitle() {
        return title;
    }

    public List<Track> getTracks() {
        return tracks;
    }

    /**
     * Represents a single track (video, audio, subtitle) within an MKV container.
     * This class is immutable.
     */
    public static final class Track {
        private final int id; // 0-based ID from JSON, used by mkvmerge
        private final String selector; // "v1", "a1", etc., used by mkvpropedit
        private final TrackType type;
        private final String codec;
        private final String trackName;
        private final boolean isDefaultTrack;
        private final boolean isEnabledTrack;
        private final String language;

        private Track(int id, String selector, TrackType type, String codec, String trackName, boolean isDefaultTrack, boolean isEnabledTrack, String language) {
            this.id = id;
            this.selector = selector;
            this.type = type;
            this.codec = codec;
            this.trackName = trackName;
            this.isDefaultTrack = isDefaultTrack;
            this.isEnabledTrack = isEnabledTrack;
            this.language = language;
        }

        /**
         * Creates a Track instance from a JSON object representing a single track.
         */
        public static Track fromJson(JSONObject trackJson, String selector) {
            JSONObject properties = trackJson.getJSONObject(KEY_PROPERTIES);
            return new Track(
                    trackJson.getInt(KEY_ID),
                    Objects.requireNonNull(selector),
                    TrackType.fromString(trackJson.getString(KEY_TYPE)),
                    trackJson.getString(KEY_CODEC),
                    properties.optString(KEY_TRACK_NAME, ""),
                    properties.optBoolean(KEY_DEFAULT_TRACK, false),
                    properties.optBoolean(KEY_ENABLED_TRACK, true),
                    properties.optString(KEY_LANGUAGE, "und")
            );
        }

        public int getId() { return id; }
        public String getSelector() { return selector; }
        public TrackType getType() { return type; }
        public String getCodec() { return codec; }
        public String getTrackName() { return trackName; }
        public boolean isDefaultTrack() { return isDefaultTrack; }
        public boolean isEnabledTrack() { return isEnabledTrack; }
        public String getLanguage() { return language; }
    }

    /**
     * An enum representing the type of an MKV track, providing type-safety.
     */
    public enum TrackType {
        VIDEO("video"),
        AUDIO("audio"),
        SUBTITLES("subtitles"),
        UNKNOWN("unknown");

        private final String jsonValue;

        TrackType(String jsonValue) {
            this.jsonValue = jsonValue;
        }

        /**
         * Returns the user-friendly, lowercase name of the track type (e.g., "video").
         * @return The display name.
         */
        public String getDisplayName() {
            return this.jsonValue;
        }

        public static TrackType fromString(String text) {
            for (TrackType b : TrackType.values()) {
                if (b.jsonValue.equalsIgnoreCase(text)) {
                    return b;
                }
            }
            // Fallback for types not explicitly handled (e.g., "buttons").
            return UNKNOWN;
        }
    }
}