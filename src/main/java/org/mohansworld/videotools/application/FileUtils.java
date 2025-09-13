package org.mohansworld.videotools.application;

import org.mohansworld.videotools.MainApp;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;

/**
 * A utility class providing helper methods for the application.
 * This includes path resolution for tools, which may be bundled or system-provided.
 */
public final class FileUtils {
    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private FileUtils() {
    }

    // A constant representing the operating system, calculated once.
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().startsWith("windows");

    // The base path of the application.
    private static String BASE_PATH;

    // A static initializer block to safely and efficiently determine the application's base path once.
    // This runs when the class is first loaded, avoiding lazy-loading race conditions in multi-threaded environments.
    static {
        try {
            // Get the location of the code source (the JAR file or classes directory).
            File source = new File(MainApp.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            // The parent directory of the JAR/classes is our application's base directory.
            BASE_PATH = source.getParentFile().getAbsolutePath();
        } catch (URISyntaxException | NullPointerException e) {
            // Fallback to the current working directory if path resolution fails.
            System.err.println("Could not determine application base path, using current directory. Error: " + e.getMessage());
            BASE_PATH = ".";
        }
    }

    /**
     * An enumeration of the external command-line tools used by the application.
     * Using an enum prevents typos and centralizes the tool names.
     */
    public enum Tool {
        FFMPEG("ffmpeg"),
        FFPROBE("ffprobe"),
        MKVMERGE("mkvmerge"),
        MKVPROPEDIT("mkvpropedit");

        private final String executableName;

        Tool(String executableName) {
            this.executableName = executableName;
        }

        public String getExecutableName() {
            return IS_WINDOWS ? executableName + ".exe" : executableName;
        }
    }

    /**
     * Gets the base directory of the running application.
     * This is the directory containing the JAR file or the launcher.
     *
     * @return The absolute path to the base directory.
     */
    public static String getApplicationBasePath() {
        return BASE_PATH;
    }

    /**
     * Gets the command or full path to a tool's executable.
     * <p>
     * <b>Behavior is platform-dependent:</b>
     * <ul>
     *     <li><b>On Linux/macOS:</b>
     *          <ul>
     *              <li>For {@code MKVMERGE} and {@code MKVPROPEDIT}, this method returns only the command name (e.g., "mkvmerge").
     *              This assumes the tools are installed system-wide and available in the user's {@code PATH}.</li>
     *              <li>For other tools (e.g., {@code FFMPEG}), it returns the path to the bundled executable in the "library" folder.</li>
     *          </ul>
     *     </li>
     *     <li><b>On Windows:</b>
     *          <ul>
     *              <li>For all tools, this method returns the full, absolute path to the bundled executable
     *              in the "library" folder.</li>
     *          </ul>
     *     </li>
     * </ul>
     *
     * @param tool The {@link FileUtils.Tool} to find the path for.
     * @return A string representing either the command name or the full path to the executable.
     */
    public static String getToolPath(FileUtils.Tool tool) {
        // On non-Windows systems, check for MKVToolNix tools first.
        if (!IS_WINDOWS) {
            switch (tool) {
                case MKVMERGE:
                case MKVPROPEDIT:
                    // On Linux/macOS, assume these are in the system's PATH.
                    // Just return the command name. ProcessBuilder will find it.
                    return tool.getExecutableName();
                // No default case here, so we fall through to the bundled path logic for other tools.
            }
        }

        // Default behavior for Windows OR for other tools (e.g., ffmpeg) on Linux/macOS:
        // Use the bundled executable in the "library" folder.
        return Path.of(getApplicationBasePath(), "library", tool.getExecutableName()).toString();
    }

    /**
     * Safely extracts the file name without its extension.
     *
     * @param filename The full filename (e.g., "video.mp4").
     * @return The file name (e.g., "video") or the full name if no extension is found.
     */
    public static String getFilenameWithoutExtension(String filename) {
        // 1. Handle null or empty input for safety
        if (filename == null || filename.isEmpty()) {
            return "";
        }

        return filename.contains(".")
                ? filename.substring(0, filename.lastIndexOf('.'))
                : filename;
    }

    /**
     * Extracts the base name from a filename like "clip-0001.mp4",
     * returning the part before the first hyphen.
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>"clip-0001.mp4" -> "clip"</li>
     *   <li>"series-episode-10.mkv" -> "series"</li>
     *   <li>"nodash.txt" -> "nodash.txt" (returns original if no hyphen is found)</li>
     *   <li>null or "" -> "" (handles empty or null input)</li>
     * </ul>
     *
     * @param filename The full filename string.
     * @return The part of the filename before the first hyphen. If no hyphen exists,
     *         the original filename is returned. Returns an empty string if the
     *         input is null or empty.
     */
    public static String getBaseFilename(String filename) {
        // 1. Handle null or empty input for safety
        if (filename == null || filename.isEmpty()) {
            return "";
        }

        // 2. Find the position of the first hyphen
        int hyphenIndex = filename.indexOf('-');

        // 3. If a hyphen is found, return the part of the string before it
        if (hyphenIndex != -1) {
            return filename.substring(0, hyphenIndex);
        }

        // 4. If no hyphen is found, return the original filename as a fallback
        return filename;
    }

    /**
     * Safely extracts the file extension without the dot.
     *
     * @param filename The full filename (e.g., "video.mp4").
     * @return The extension (e.g., "mp4") or an empty string if no extension is found.
     */
    public static String getFileExtension(String filename) {
        // 1. Handle null or empty input for safety
        if (filename == null || filename.isEmpty()) {
            return "";
        }

        // 2. Find the position of the last dot
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex != -1) {
            return filename.substring(dotIndex+1);
        }

        // 3. If no dot is found, return the original filename as a fallback
        return filename;
    }
}