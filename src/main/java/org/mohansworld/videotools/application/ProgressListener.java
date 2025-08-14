package org.mohansworld.videotools.application;

/**
 * A listener interface for receiving status updates from a long-running process.
 * <p>
 * Implementations of this interface can be used to report progress to a user interface,
 * a console, a logging file, or any other target. The lifecycle of a monitored
 * process should follow one of these patterns:
 * <ul>
 *   <li>(onProgress)* -> onComplete</li>
 *   <li>(onProgress)* -> onError</li>
 * </ul>
 */
public interface ProgressListener {

    /**
     * Called periodically to report a progress update from the running process.
     * This method may be called zero or more times before the process completes or fails.
     *
     * @param message The progress message. Should not be null.
     */
    void onProgress(String message);

    /**
     * Called once when the process completes successfully.
     * No more calls to this listener will be made for the process after this method is called.
     *
     * @param finalMessage The final success message summarizing the result. Can be null.
     */
    void onComplete(String finalMessage);

    /**
     * Called once when the process encounters an error or fails.
     * No more calls to this listener will be made for the process after this method is called.
     *
     * @param message The description of the error. Should not be null.
     */
    void onError(String message);
    void clearLog();
}