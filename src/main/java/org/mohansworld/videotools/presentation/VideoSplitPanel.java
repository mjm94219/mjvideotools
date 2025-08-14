package org.mohansworld.videotools.presentation;

import net.miginfocom.swing.MigLayout;
import org.mohansworld.videotools.application.FfVideoProcessor;
import org.mohansworld.videotools.application.ProgressListener;
import org.mohansworld.videotools.application.VideoSplitter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

/**
 * A JPanel for splitting a video file into smaller segments of a specified duration.
 * It provides UI components for selecting an input file, defining the split length,
 * and initiating the split process. The processing is handled on a background thread
 * to keep the UI responsive.
 */
public class VideoSplitPanel extends JPanel {

    // --- UI Constants ---
    private static final String DEFAULT_SPLIT_LENGTH = "00:10:00";
    private static final String DURATION_REGEX = "\\d{2}:\\d{2}:\\d{2}";
    private static final Pattern DURATION_PATTERN = Pattern.compile(DURATION_REGEX);
    private static final String[] VIDEO_FILE_EXTENSIONS = {"mp4", "mkv", "mov", "avi", "flv"};

    // --- Component Text & Messages ---
    private static final String LABEL_INPUT_VIDEO = "Input Video:";
    private static final String LABEL_SPLIT_LENGTH = "Split Length (HH:MM:SS):";
    private static final String BUTTON_BROWSE = "Browse...";
    private static final String BUTTON_SPLIT = "Split Video";
    private static final String BUTTON_CLEAR = "Clear All";
    private static final String ERROR_TITLE_INPUT = "Input Error";
    private static final String ERROR_TITLE_GENERIC = "Error";
    private static final String ERROR_MSG_NO_INPUT_FILE = "Please select an input video file.";
    private static final String ERROR_MSG_INVALID_DURATION = "Invalid duration format. Use HH:MM:SS.";
    private static final String PROGRESS_MSG_STARTING_SPLIT = "Starting video split operation...";
    private static final String ERROR_MSG_UNEXPECTED = "An unexpected error occurred during the split operation: ";

    // --- UI Components ---
    private final JTextField inputFileField;
    private final JTextField splitLengthField;
    private final JButton actionButton;
    private final JButton browseButton;
    private final JButton clearAllButton;

    // --- Application Logic & Dependencies ---
    private final VideoSplitter videoSplitter;
    private final ProgressListener progressListener;

    /**
     * Constructs the VideoSplitPanel.
     *
     * @param videoProcessor   The video processor implementation to be used for splitting.
     * @param progressListener The listener to report progress and errors to.
     */
    public VideoSplitPanel(FfVideoProcessor videoProcessor, ProgressListener progressListener) {
        this.videoSplitter = new VideoSplitter(videoProcessor);
        this.progressListener = progressListener;

        // Initialize all UI components
        this.inputFileField = new JTextField();
        this.splitLengthField = new JTextField(DEFAULT_SPLIT_LENGTH);
        this.actionButton = new JButton(BUTTON_SPLIT);
        this.browseButton = new JButton(BUTTON_BROWSE);
        this.clearAllButton = new JButton(BUTTON_CLEAR);

        // Organize the panel's construction into logical steps
        setupLayout();
        addListeners();
        updateButtonStates();
    }

    /**
     * Clears all input fields and the progress log.
     */
    public void clearPanel() {
        inputFileField.setText("");
        progressListener.clearLog();
        updateButtonStates();
    }

    //-------------------------------------------------------------------------
    //- PRIVATE HELPER METHODS
    //-------------------------------------------------------------------------

    /**
     * Configures the panel's layout and adds all components.
     */
    private void setupLayout() {
        setLayout(new MigLayout("insets 10, fillx", "[][grow]", "[]"));

        // Row 0: Input File Selection
        add(new JLabel(LABEL_INPUT_VIDEO));
        add(inputFileField, "split 2, growx");
        add(browseButton, "wrap");

        // Row 1: Split Length Input
        add(new JLabel(LABEL_SPLIT_LENGTH));
        add(splitLengthField, "wrap");

        // Row 2: Action Buttons
        add(actionButton, "cell 0 2, span 2, split 2, gaptop 10");
        add(clearAllButton, "align right");
    }

    /**
     * Attaches action listeners to the interactive components.
     */
    private void addListeners() {
        browseButton.addActionListener(e -> chooseInputFile());
        actionButton.addActionListener(e -> executeSplitVideo());
        clearAllButton.addActionListener(e -> clearPanel());

        // Use the simple listener to update button state whenever the text field changes.
        inputFileField.getDocument().addDocumentListener(new SimpleDocumentListener(this::updateButtonStates));
    }

    /**
     * Enables or disables the action button based on whether an input file has been selected.
     */
    private void updateButtonStates() {
        boolean hasInputFile = !inputFileField.getText().trim().isEmpty();
        actionButton.setEnabled(hasInputFile);
    }

    /**
     * Opens a file chooser to select a video file.
     */
    private void chooseInputFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("Video Files", VIDEO_FILE_EXTENSIONS));
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            inputFileField.setText(selectedFile.getAbsolutePath());
        }
    }

    /**
     * Validates input and initiates the video splitting process on a background thread.
     */
    private void executeSplitVideo() {
        String inputPath = inputFileField.getText().trim();
        if (inputPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, ERROR_MSG_NO_INPUT_FILE, ERROR_TITLE_INPUT, JOptionPane.ERROR_MESSAGE);
            return;
        }

        String segmentTime = splitLengthField.getText().trim();
        if (!DURATION_PATTERN.matcher(segmentTime).matches()) {
            JOptionPane.showMessageDialog(this, ERROR_MSG_INVALID_DURATION, ERROR_TITLE_GENERIC, JOptionPane.ERROR_MESSAGE);
            return;
        }

        progressListener.clearLog();
        progressListener.onProgress(PROGRESS_MSG_STARTING_SPLIT);
        setComponentsEnabled(false);

        // SwingWorker performs the long task on a background thread to keep UI responsive.
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                videoSplitter.execute(inputPath, segmentTime, progressListener);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // Propagate exceptions from the background thread.
                } catch (InterruptedException | ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    progressListener.onError(ERROR_MSG_UNEXPECTED + cause.getMessage());
                } finally {
                    setComponentsEnabled(true); // Always re-enable UI components.
                }
            }
        }.execute();
    }

    /**
     * A utility method to enable or disable all interactive components on the panel.
     * @param enabled true to enable, false to disable.
     */
    private void setComponentsEnabled(boolean enabled) {
        actionButton.setEnabled(enabled);
        browseButton.setEnabled(enabled);
        clearAllButton.setEnabled(enabled);
        inputFileField.setEnabled(enabled);
        splitLengthField.setEnabled(enabled);
        if (enabled) {
            updateButtonStates(); // Re-check state after enabling
        }
    }

    //-------------------------------------------------------------------------
    //- NESTED HELPER CLASS
    //-------------------------------------------------------------------------

    /**
     * A simple implementation of DocumentListener that calls a lambda on any change.
     * Declared as a private static nested class because it's an implementation detail
     * of VideoSplitPanel and doesn't need access to its instance members.
     */
    private record SimpleDocumentListener(Runnable updateAction) implements DocumentListener {
        @Override
        public void insertUpdate(DocumentEvent e) {
            updateAction.run();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            updateAction.run();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            updateAction.run();
        }
    }
}