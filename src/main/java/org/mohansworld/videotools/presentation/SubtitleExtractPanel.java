package org.mohansworld.videotools.presentation;

import net.miginfocom.swing.MigLayout;
import org.mohansworld.videotools.application.FfVideoProcessor;
import org.mohansworld.videotools.application.ProgressListener;
import org.mohansworld.videotools.application.SubtitleExtractor;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;

/**
 * A JPanel for extracting subtitles from a video file.
 * <p>
 * This panel provides UI components for selecting an input video file and
 * initiating the subtitle extraction process. It uses a {@link SwingWorker}
 * to perform the extraction on a background thread, preventing the UI from freezing.
 * Progress and results are communicated via a {@link ProgressListener}.
 */
public class SubtitleExtractPanel extends JPanel {

    // --- UI Constants ---
    private static final String LAYOUT_CONSTRAINTS = "insets 10, fillx";
    private static final String COMPONENT_CONSTRAINTS = "[][grow]";
    private static final String ROW_CONSTRAINTS = "[]";

    private static final String LABEL_INPUT_VIDEO = "Input Video:";
    private static final String BUTTON_BROWSE = "Browse...";
    private static final String BUTTON_EXTRACT = "Extract Subtitles";
    private static final String BUTTON_CLEAR = "Clear All";

    private static final String FILE_CHOOSER_TITLE = "Select Video File";
    private static final String FILE_CHOOSER_FILTER_DESC = "Video Files (mp4, mkv, mov)";

    private static final String DIALOG_INPUT_ERROR_TITLE = "Input Error";
    private static final String DIALOG_INPUT_ERROR_MESSAGE = "Please select an input video file.";

    // --- Dependencies ---
    private final SubtitleExtractor subtitleExtractor;
    private final ProgressListener progressListener;

    // --- UI Components ---
    private JTextField inputFileField;
    private JButton extractButton;
    private JButton clearAllButton;
    private JButton browseButton;
    private JFileChooser fileChooser; // Lazily initialized for better performance

    /**
     * Constructs the SubtitleExtractPanel.
     *
     * @param videoProcessor   The video processing engine to be used by the extractor.
     * @param progressListener A listener to report progress, completion, and errors.
     */
    public SubtitleExtractPanel(FfVideoProcessor videoProcessor, ProgressListener progressListener) {
        this.subtitleExtractor = new SubtitleExtractor(videoProcessor);
        this.progressListener = progressListener;

        initComponents();
        initLayout();
        addListeners();

        updateButtonStates();
    }

    /**
     * Clears all input fields and resets the progress log.
     */
    public void clearPanel() {
        inputFileField.setText("");
        progressListener.clearLog();
        updateButtonStates();
    }

    /**
     * Initializes all UI components.
     */
    private void initComponents() {
        inputFileField = new JTextField();
        browseButton = new JButton(BUTTON_BROWSE);
        extractButton = new JButton(BUTTON_EXTRACT);
        clearAllButton = new JButton(BUTTON_CLEAR);
    }

    /**
     * Sets up the panel layout and adds all components.
     */
    private void initLayout() {
        setLayout(new MigLayout(LAYOUT_CONSTRAINTS, COMPONENT_CONSTRAINTS, ROW_CONSTRAINTS));

        // --- Row 1: Input File Selection ---
        add(new JLabel(LABEL_INPUT_VIDEO));
        add(inputFileField, "split 2, growx"); // 'split 2' means this and the next component share a cell
        add(browseButton, "wrap"); // 'wrap' moves to the next row

        // --- Row 2: (Spacer) ---
        // An empty row is implicitly created by "wrap" above.

        // --- Row 3: Action Buttons ---
        // "sgx button" groups the extract and clear buttons to have the same size.
        // "gaptop 10" adds a 10px vertical gap above this component.
        // The buttons are placed into a "flowy" panel to keep them together if the window is resized.
        add(extractButton, "cell 0 2, gaptop 10, sgx button");
        add(clearAllButton, "sgx button");
    }

    /**
     * Attaches action listeners to the interactive components.
     */
    private void addListeners() {
        browseButton.addActionListener(e -> chooseInputFile());
        clearAllButton.addActionListener(e -> clearPanel());
        extractButton.addActionListener(e -> handleExtractionRequest());

        // Update button state not just on clicks, but also on text changes using our helper.
        inputFileField.getDocument().addDocumentListener(new SimpleDocumentListener(this::updateButtonStates));
    }


    /**
     * Enables or disables the primary action button based on UI state.
     * The extract button should only be enabled if an input file is specified.
     */
    private void updateButtonStates() {
        boolean hasInput = !inputFileField.getText().trim().isEmpty();
        extractButton.setEnabled(hasInput);
    }

    /**
     * Opens a file chooser dialog to select a video file.
     * The selected file path is placed into the input text field.
     */
    private void chooseInputFile() {
        // Lazily initialize the file chooser for better startup performance
        if (fileChooser == null) {
            fileChooser = new JFileChooser();
            fileChooser.setDialogTitle(FILE_CHOOSER_TITLE);
            fileChooser.setFileFilter(new FileNameExtensionFilter(FILE_CHOOSER_FILTER_DESC, "mp4", "mkv", "mov"));
        }

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            inputFileField.setText(selectedFile.getAbsolutePath());
            // The document listener on inputFileField will automatically call updateButtonStates()
        }
    }

    /**
     * Validates input and initiates the subtitle extraction process.
     */
    private void handleExtractionRequest() {
        String inputPath = inputFileField.getText().trim();
        if (inputPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, DIALOG_INPUT_ERROR_MESSAGE, DIALOG_INPUT_ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
            return;
        }

        progressListener.clearLog();
        performExtraction(inputPath);
    }

    /**
     * Executes the subtitle extraction task on a background thread using a SwingWorker.
     * This prevents the UI from freezing during the potentially long-running operation.
     *
     * @param inputPath The absolute path to the input video file.
     */
    private void performExtraction(String inputPath) {
        // Disable button to prevent multiple concurrent operations
        extractButton.setEnabled(false);
        clearAllButton.setEnabled(false);

        // SwingWorker is essential for long-running tasks in Swing.
        // 'doInBackground' runs on a worker thread.
        // 'done' runs on the Event Dispatch Thread (EDT) after the background task finishes.
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                progressListener.onProgress("Starting subtitle extraction...");
                // The core logic is delegated to the application layer
                subtitleExtractor.execute(inputPath, progressListener);
                return null;
            }

            @Override
            protected void done() {
                try {
                    // Call get() to re-throw any exceptions that occurred in doInBackground()
                    get();
                    progressListener.onProgress("Subtitle extraction finished successfully.");
                } catch (Exception e) {
                    // Log any errors that occurred during the background task
                    progressListener.onError("An error occurred during extraction: " + e.getMessage());
                } finally {
                    // Always re-enable the buttons, whether the task succeeded or failed.
                    clearAllButton.setEnabled(true);
                    updateButtonStates();
                }
            }
        }.execute();
    }

    /**
     * A private, static, nested helper class to simplify listening to document changes.
     * Using this adapter allows us to provide a single lambda or method reference
     * instead of implementing all three methods of the {@link DocumentListener} interface.
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
            // Usually not fired for plain text components
            updateAction.run();
        }
    }
}