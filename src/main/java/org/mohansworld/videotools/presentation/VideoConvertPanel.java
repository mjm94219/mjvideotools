package org.mohansworld.videotools.presentation;

import net.miginfocom.swing.MigLayout;
import org.mohansworld.videotools.application.FfVideoProcessor;
import org.mohansworld.videotools.application.ProgressListener;
import org.mohansworld.videotools.application.VideoConverter;
import org.mohansworld.videotools.domain.VideoFormat;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.concurrent.ExecutionException;

/**
 * A JPanel for converting video files from one format to another.
 * It provides UI for selecting an input file, choosing an output format,
 * and initiating the conversion process.
 */
public class VideoConvertPanel extends JPanel {

    // --- Business Logic Components ---
    private final VideoConverter videoConverter;
    private final ProgressListener progressListener;

    // --- UI Components ---
    private JTextField inputFileField;
    private JComboBox<VideoFormat> formatComboBox;
    private JButton convertButton; // Renamed from ActionButton for clarity
    private JButton browseButton;
    private JButton clearAllButton;

    /**
     * Constructs the video conversion panel.
     *
     * @param videoProcessor   The underlying video processing engine (e.g., FFmpeg wrapper).
     * @param progressListener A listener to which progress and log messages will be sent.
     */
    public VideoConvertPanel(FfVideoProcessor videoProcessor, ProgressListener progressListener) {
        this.videoConverter = new VideoConverter(videoProcessor);
        this.progressListener = progressListener;

        // The setup process is broken into logical steps for clarity and maintainability.
        initializeComponents();
        layoutComponents();
        registerListeners();

        // Set the initial state of the UI.
        updateButtonStates();
    }

    /**
     * Creates and configures the UI components.
     */
    private void initializeComponents() {
        inputFileField = new JTextField();
        formatComboBox = new JComboBox<>(VideoFormat.values());

        browseButton = new JButton("Browse...");
        // Setting a fixed width can help with layout consistency.
        browseButton.setPreferredSize(new Dimension(100, (int) browseButton.getPreferredSize().getHeight()));

        convertButton = new JButton("Convert Video");
        convertButton.setPreferredSize(new Dimension(150, (int) convertButton.getPreferredSize().getHeight()));

        clearAllButton = new JButton("Clear All");
        clearAllButton.setPreferredSize(new Dimension(150, (int) clearAllButton.getPreferredSize().getHeight()));
    }

    /**
     * Arranges the UI components on the panel using MigLayout.
     */
    private void layoutComponents() {
        // "insets 10" adds a 10px border around the panel.
        // "fillx" makes components in growing columns fill the horizontal space.
        // "[][grow]" defines two columns: the first for labels (natural size), the second for inputs (grows to fill space).
        // "[]" defines rows that take their natural height.
        setLayout(new MigLayout("insets 10, fillx", "[][grow]", "[][]"));

        // --- Row 0: Input File Selection ---
        add(new JLabel("Input Video:"));
        // "split 2" tells MigLayout to place the next 2 components in the same cell.
        // "growx" makes the text field expand horizontally.
        add(inputFileField, "split 2, growx");
        // "wrap" ends the current row.
        add(browseButton, "wrap");

        // --- Row 1: Output Format Selection ---
        add(new JLabel("Output Video Format:"));
        add(formatComboBox, "wrap");

        // --- Row 2: Action Buttons ---
        // "cell 0 2" places this component explicitly in column 0, row 2.
        // "span 2" makes the component's cell span across both columns.
        // "split 2" divides this spanned cell into two parts for the two buttons.
        // "gaptop 10" adds a 10px vertical gap above this row.
        add(convertButton, "cell 0 2, span 2, split 2, gaptop 10");
        // "align right" places this component at the far right of its cell.
        add(clearAllButton, "align right");
    }

    /**
     * Registers action listeners for interactive components.
     */
    private void registerListeners() {
        browseButton.addActionListener(e -> chooseInputFile());
        convertButton.addActionListener(e -> startVideoConversion());
        clearAllButton.addActionListener(e -> clearPanel());

        // Add a listener to the input text field to enable/disable the convert button
        // in real-time as the user types or clears the field.
        inputFileField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateButtonStates();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                updateButtonStates();
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                updateButtonStates();
            }
        });
    }

    /**
     * Clears the input field and the progress log.
     */
    public void clearPanel() {
        inputFileField.setText("");
        progressListener.clearLog();
        // The DocumentListener on inputFileField will automatically call updateButtonStates().
    }

    /**
     * Updates the enabled state of buttons based on the current UI state.
     * The "Convert" button should only be enabled if an input file path is present.
     */
    private void updateButtonStates() {
        boolean hasInput = !inputFileField.getText().trim().isEmpty();
        convertButton.setEnabled(hasInput);
    }

    /**
     * Opens a file chooser dialog for the user to select an input video file.
     */
    private void chooseInputFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("Video Files", "mp4", "mkv", "mov", "avi", "webm"));
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            inputFileField.setText(selectedFile.getAbsolutePath());
            // The DocumentListener will handle the call to updateButtonStates().
        }
    }

    /**
     * Validates input and initiates the video conversion process in a background thread.
     */
    private void startVideoConversion() {
        String inputPath = inputFileField.getText().trim();
        // The button state should prevent this, but it's good practice to double-check.
        if (inputPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select an input video file.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        VideoFormat selectedFormat = (VideoFormat) formatComboBox.getSelectedItem();
        if (selectedFormat == null) {
            JOptionPane.showMessageDialog(this, "Please select an output format.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        progressListener.clearLog();

        // Disable the button to prevent multiple concurrent conversions of the same file.
        convertButton.setEnabled(false);
        clearAllButton.setEnabled(false); // Also disable clear to avoid confusion during conversion.

        // Execute the long-running conversion task in a background thread.
        VideoConversionWorker worker = new VideoConversionWorker(inputPath, selectedFormat);
        worker.execute();
    }

    /**
     * A SwingWorker to handle the video conversion in the background, ensuring the UI remains responsive.
     */
    private class VideoConversionWorker extends SwingWorker<Void, Void> {
        private final String inputPath;
        private final VideoFormat outputFormat;

        public VideoConversionWorker(String inputPath, VideoFormat outputFormat) {
            this.inputPath = inputPath;
            this.outputFormat = outputFormat;
        }

        /**
         * This method is executed on a background thread.
         * It contains the long-running video conversion logic.
         */
        @Override
        protected Void doInBackground() {
            // The actual conversion is delegated to the videoConverter.
            videoConverter.execute(inputPath, outputFormat, progressListener);
            return null;
        }

        /**
         * This method is executed on the Event Dispatch Thread (EDT) after doInBackground completes.
         * It is used to update the UI post-conversion.
         */
        @Override
        protected void done() {
            try {
                // Call get() to retrieve the result, which will also re-throw any
                // exceptions that occurred during doInBackground().
                get();
                progressListener.onComplete("Conversion completed successfully!");
            } catch (InterruptedException e) {
                // This can happen if the task is cancelled.
                Thread.currentThread().interrupt(); // Preserve the interrupted status.
                progressListener.onComplete("Conversion was cancelled.");
            } catch (ExecutionException e) {
                // This wraps the actual exception from the background task.
                Throwable cause = e.getCause();
                progressListener.onError("ERROR: " + cause.getMessage());
            } finally {
                // Always re-enable the buttons, regardless of success or failure.
                updateButtonStates(); // Re-evaluates state, enabling convertButton if input is still present.
                clearAllButton.setEnabled(true);
            }
        }
    }
}