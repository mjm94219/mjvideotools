package org.mohansworld.videotools.presentation;

import net.miginfocom.swing.MigLayout;
import org.mohansworld.videotools.application.AudioExtractor;
import org.mohansworld.videotools.application.FfVideoProcessor;
import org.mohansworld.videotools.application.ProgressListener;
import org.mohansworld.videotools.domain.AudioFormat;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;

/**
 * A JPanel that provides a user interface for extracting audio from a video file.
 * It allows the user to select an input video, choose an output audio format,
 * and initiate the extraction process. Progress and results are communicated
 * via a {@link ProgressListener}.
 */
public class AudioExtractPanel extends JPanel {

    // UI Components are final as they are initialized once and never reassigned.
    private final JTextField inputFileField;
    private final JComboBox<AudioFormat> formatComboBox;
    private final JButton actionButton;
    private final JButton clearAllButton;
    private final JButton browseButton;

    // Application Logic Services
    private final AudioExtractor audioExtractor;
    private final ProgressListener progressListener;

    /**
     * Constructs the audio extraction panel.
     *
     * @param videoProcessor   The video processing backend to be used by the {@link AudioExtractor}.
     * @param progressListener A listener to report progress, errors, and completion messages to.
     */
    public AudioExtractPanel(FfVideoProcessor videoProcessor, ProgressListener progressListener) {
        this.audioExtractor = new AudioExtractor(videoProcessor);
        this.progressListener = progressListener;

        // --- Component Initialization ---
        // Initialize fields directly within the constructor to satisfy the 'final' keyword contract.
        inputFileField = new JTextField();
        browseButton = new JButton("Browse...");
        formatComboBox = new JComboBox<>(AudioFormat.values());
        actionButton = new JButton("Extract Audio");
        clearAllButton = new JButton("Clear All");

        // Decompose layout and listener registration into helper methods for better readability.
        layoutComponents();
        registerListeners();

        // Set the initial state of the buttons.
        updateButtonStates();
    }

    /**
     * Lays out all the initialized components on the panel using MigLayout.
     */
    private void layoutComponents() {
        // "insets 10" adds a 10px border around the container.
        // "fillx" makes components expand horizontally to fill available space.
        // "[][grow]" defines two columns: the first for labels (default size), the second grows.
        // "[]" defines rows that take their preferred height.
        setLayout(new MigLayout("insets 10, fillx", "[][grow]", "[]"));

        // --- Row 0: Input File Selection ---
        add(new JLabel("Input Video:"));
        // "split 2" tells MigLayout that the next 2 components share the same cell.
        // "growx" makes the text field expand horizontally.
        add(inputFileField, "split 2, growx");
        add(browseButton, "wrap"); // "wrap" ends the current row.

        // --- Row 1: Output Format Selection ---
        add(new JLabel("Output Audio Format:"));
        add(formatComboBox, "wrap");

        // --- Row 2: Action Buttons ---
        // "gaptop 10" adds a 10px gap above this component.
        // "sg buttons" puts this button in the 'buttons' size group, making them the same size.
        // "w 150!" sets the width of the size group to 150 pixels.
        add(actionButton, "cell 0 2, gaptop 10, sg buttons, w 150!");
        add(clearAllButton, "cell 1 2, gaptop 10, sg buttons, w 150!");
    }

    /**
     * Registers all event listeners for the interactive components.
     */
    private void registerListeners() {
        browseButton.addActionListener(e -> chooseInputFile());
        actionButton.addActionListener(e -> extractAudio());
        clearAllButton.addActionListener(e -> clearPanel());

        // A DocumentListener ensures updateButtonStates() is called on any text change,
        // including paste operations, which an ActionListener on the field would miss.
        inputFileField.getDocument().addDocumentListener(new SimpleDocumentListener(this::updateButtonStates));
    }


    /**
     * Resets the panel to its initial state by clearing the input field and logs.
     */
    public void clearPanel() {
        inputFileField.setText("");
        progressListener.clearLog();
        // updateButtonStates() is called automatically by the DocumentListener
    }

    /**
     * Enables or disables the action button based on whether an input file is selected.
     */
    private void updateButtonStates() {
        // Use trim() to ensure whitespace-only input doesn't enable the button.
        boolean hasInput = !inputFileField.getText().trim().isEmpty();
        actionButton.setEnabled(hasInput);
    }

    /**
     * Opens a file chooser dialog to allow the user to select an input video file.
     */
    private void chooseInputFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("Video Files", "mp4", "mkv", "mov", "avi", "webm"));
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            inputFileField.setText(selectedFile.getAbsolutePath());
            // No need to call updateButtonStates() here because the DocumentListener will do it.
        }
    }

    /**
     * Validates user input and initiates the audio extraction process on a background thread.
     */
    private void extractAudio() {
        String inputPath = inputFileField.getText().trim();
        if (inputPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select an input video file.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        AudioFormat selectedFormat = (AudioFormat) formatComboBox.getSelectedItem();
        if (selectedFormat == null) {
            JOptionPane.showMessageDialog(this, "Please select an output audio format.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        progressListener.clearLog();

        // Disable UI controls to prevent concurrent operations.
        actionButton.setEnabled(false);
        browseButton.setEnabled(false);

        // Use SwingWorker to perform the long-running task off the Event Dispatch Thread (EDT),
        // preventing the UI from freezing.
        new SwingWorker<Void, String>() { // Void result, String for progress updates (if needed)
            @Override
            protected Void doInBackground() {
                progressListener.onProgress("Starting audio extraction...");
                // The actual extraction logic is delegated to the application service.
                audioExtractor.execute(inputPath, selectedFormat, progressListener);
                return null; // A Void task returns null.
            }

            @Override
            protected void done() {
                // This method is executed on the EDT after doInBackground() finishes.
                try {
                    // Call get() to retrieve the result. More importantly, this re-throws any
                    // exceptions that occurred in doInBackground(), allowing us to handle them here.
                    get();
                    progressListener.onComplete("Audio extraction completed successfully!");
                } catch (Exception e) {
                    // Log any errors that occurred during the background task.
                    progressListener.onError("An error occurred during audio extraction: " + e.getCause().getMessage());
                } finally {
                    // CRITICAL: Re-enable the UI controls in a 'finally' block to ensure
                    // they are always re-enabled, even if an exception occurred.
                    updateButtonStates(); // Re-enable action button if input is still valid
                    browseButton.setEnabled(true);
                }
            }
        }.execute();
    }

    /**
     * A simple DocumentListener that accepts a lambda for its update method.
     * This avoids the need for verbose anonymous inner classes for all three methods
     * (insertUpdate, removeUpdate, changedUpdate) when we only need one action.
     */
    private record SimpleDocumentListener(Runnable update) implements DocumentListener {

        @Override
        public void insertUpdate(DocumentEvent e) {
            update.run();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            update.run();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            // Plain text components do not fire this event.
        }
    }
}