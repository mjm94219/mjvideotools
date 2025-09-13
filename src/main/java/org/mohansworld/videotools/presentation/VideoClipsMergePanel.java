package org.mohansworld.videotools.presentation;

import net.miginfocom.swing.MigLayout;
import org.mohansworld.videotools.application.FfVideoProcessor;
import org.mohansworld.videotools.application.FileUtils;
import org.mohansworld.videotools.application.ProgressListener;
import org.mohansworld.videotools.application.VideoClipsMerger;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * A JPanel that provides a user interface for selecting, ordering, and merging multiple video clips
 * into a single output file. It handles file selection, reordering, output format selection,
 * and executes the merge operation on a background thread to keep the UI responsive.
 */
public class VideoClipsMergePanel extends JPanel {

    // --- UI Components ---
    private DefaultListModel<String> listModel;
    private JList<String> fileList;
    private JButton removeButton;
    private JButton moveUpButton;
    private JButton moveDownButton;
    private JComboBox<String> formatComboBox;
    private JTextField outputField;
    private JButton actionButton;

    // --- Application Logic Dependencies ---
    private final VideoClipsMerger videoClipsMerger;
    private final ProgressListener progressListener;

    /**
     * Constructs the video clips merging panel.
     *
     * @param videoProcessor   The video processing engine to be used for the merge operation.
     * @param progressListener A listener to report progress and errors back to the user.
     */
    public VideoClipsMergePanel(FfVideoProcessor videoProcessor, ProgressListener progressListener) {
        this.videoClipsMerger = new VideoClipsMerger(videoProcessor);
        this.progressListener = progressListener;

        initComponents();
        initLayout();
        initListeners();
        setInitialState();
    }

    /**
     * Resets the panel to its initial state by clearing the file list and progress log.
     */
    public void clearPanel() {
        listModel.clear();
        progressListener.clearLog();
        updateButtonStates();
        updateOutputFilename(null);
    }

    /**
     * Initializes all the Swing components for the panel.
     */
    private void initComponents() {
        // File list components
        listModel = new DefaultListModel<>();
        fileList = new JList<>(listModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Control buttons
        removeButton = new JButton("Remove Selected");
        moveUpButton = new JButton("Move Up");
        moveDownButton = new JButton("Move Down");

        // Output configuration components
        formatComboBox = new JComboBox<>(new String[]{"mkv", "mp4", "mov"});
        outputField = new JTextField();

        // Action buttons
        actionButton = new JButton("Start Merging");
    }

    /**
     * Configures the layout of the panel and adds all components.
     * This method uses MigLayout to arrange the components.
     */
    private void initLayout() {
        setLayout(new MigLayout(
                "insets 10, fill",    // Layout constraints
                "[grow, fill]20[250!]", // Column constraints: one growing, one fixed at 250px
                "[][grow, fill][]"     // Row constraints
        ));

        // --- Left side: File List ---
        JScrollPane scrollPane = new JScrollPane(fileList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Video Clips to Merge (in order)"));
        add(scrollPane, "cell 0 0, spany 2, grow");

        // --- Right side (top): List manipulation buttons ---
        JPanel buttonPanel = new JPanel(new MigLayout("wrap 1, fillx"));
        buttonPanel.add(new JButton("Add Video Clips..."), "growx");
        buttonPanel.add(moveUpButton, "growx, gaptop 10");
        buttonPanel.add(moveDownButton, "growx");
        buttonPanel.add(removeButton, "growx, gaptop 10");
        add(buttonPanel, "cell 1 0");

        // --- Right side (middle): Output options ---
        JPanel outputPanel = new JPanel(new MigLayout("wrap 2, fillx", "[][grow,fill]"));
        outputPanel.setBorder(BorderFactory.createTitledBorder("Output"));
        outputPanel.add(new JLabel("Format:"));
        outputPanel.add(formatComboBox, "growx");
        outputPanel.add(new JLabel("Output File:"));
        outputPanel.add(outputField, "growx, split 2");
        outputPanel.add(new JButton("..."));
        add(outputPanel, "cell 1 1, growx, gaptop 10");

        // --- Bottom row: Main action buttons ---
        JPanel bottomControlsPanel = new JPanel(new MigLayout("insets 0, nogrid, fillx"));
        bottomControlsPanel.add(actionButton, "split 2, width 150!");
        bottomControlsPanel.add(new JButton("Clear All"), "width 150!");
        add(bottomControlsPanel, "cell 0 2, spanx 2, gaptop 10");
    }

    /**
     * Registers all action listeners for the interactive components.
     */
    private void initListeners() {
        // The first button in buttonPanel is 'Add Video Clips...'
        ((JButton) ((JPanel) getComponent(1)).getComponent(0)).addActionListener(this::addFiles);
        moveUpButton.addActionListener(this::moveUp);
        moveDownButton.addActionListener(this::moveDown);
        removeButton.addActionListener(this::removeSelected);

        // The last component in outputPanel is the browse '...' button
        ((JButton) ((JPanel) getComponent(2)).getComponent(4)).addActionListener(this::browseForOutput);
        formatComboBox.addActionListener(this::updateOutputFilename);

        // Main action buttons
        actionButton.addActionListener(this::mergeClips);
        // The second button in bottomControlsPanel is 'Clear All'
        ((JButton) ((JPanel) getComponent(3)).getComponent(1)).addActionListener(e -> clearPanel());

        // Update UI state whenever the list selection changes
        fileList.addListSelectionListener(e -> updateButtonStates());
    }

    /**
     * Sets the initial state of the UI components after creation.
     */
    private void setInitialState() {
        updateOutputFilename(null);
        updateButtonStates();
    }

    /**
     * Enables or disables buttons based on the current selection and number of items in the list.
     */
    private void updateButtonStates() {
        final int selectedIndex = fileList.getSelectedIndex();
        final boolean isSelected = selectedIndex != -1;
        final boolean canMoveUp = isSelected && selectedIndex > 0;
        final boolean canMoveDown = isSelected && selectedIndex < listModel.getSize() - 1;

        removeButton.setEnabled(isSelected);
        moveUpButton.setEnabled(canMoveUp);
        moveDownButton.setEnabled(canMoveDown);

        // Merging is only possible if there are at least two clips
        actionButton.setEnabled(listModel.getSize() > 1);
    }

    /**
     * Handles the 'Add Video Clips' action. Opens a file chooser to select video files.
     * @param e The action event (unused).
     */
    private void addFiles(ActionEvent e) {
        final JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("Video Files", "mp4", "mkv", "mov"));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            for (File file : chooser.getSelectedFiles()) {
                listModel.addElement(file.getAbsolutePath());
            }
        }
        updateButtonStates();
        updateOutputFilename(null);
    }

    /**
     * Handles the 'Remove Selected' action. Removes the currently selected file from the list.
     * @param e The action event (unused).
     */
    private void removeSelected(ActionEvent e) {
        final int selectedIndex = fileList.getSelectedIndex();
        if (selectedIndex != -1) {
            listModel.remove(selectedIndex);
        }
        updateButtonStates();
        updateOutputFilename(null);
    }

    /**
     * Handles the 'Move Up' action. Moves the selected item one position up in the list.
     * @param e The action event (unused).
     */
    private void moveUp(ActionEvent e) {
        final int index = fileList.getSelectedIndex();
        if (index > 0) {
            final String item = listModel.remove(index);
            listModel.add(index - 1, item);
            fileList.setSelectedIndex(index - 1); // Keep the moved item selected
            if (index == 1) { // The first element has changed
                updateOutputFilename(null);
            }
        }
    }

    /**
     * Handles the 'Move Down' action. Moves the selected item one position down in the list.
     * @param e The action event (unused).
     */
    private void moveDown(ActionEvent e) {
        final int index = fileList.getSelectedIndex();
        if (index > -1 && index < listModel.getSize() - 1) {
            final String item = listModel.remove(index);
            listModel.add(index + 1, item);
            fileList.setSelectedIndex(index + 1); // Keep the moved item selected
            if (index == 0) { // The first element has changed
                updateOutputFilename(null);
            }
        }
    }

    /**
     * Updates the output filename field. If clips are present in the list, it generates a
     * name based on the first clip (e.g., "clip-0001.mp4" becomes "clip-merged.mkv").
     * The suggested output directory is the same as the first clip's directory.
     * If the list is empty, it defaults to "merged.mkv" in the user's home directory.
     *
     * @param e The action event (unused, can be null when called directly).
     */
    private void updateOutputFilename(ActionEvent e) {
        String baseName;
        String directory;

        if (!listModel.isEmpty()) {
            // Use the first file in the list as the base for the output name
            String firstFilePath = listModel.getElementAt(0);
            File firstFile = new File(firstFilePath);

            // Get the filename (e.g., "clip-0001.mp4")
            String firstFileName = firstFile.getName();

            // Generate the new base name (e.g., "clip-merged")
            baseName = FileUtils.getBaseFilename(firstFileName) + "-merged";

            // Use the parent directory of the first file for the output
            directory = firstFile.getParent();
            if (directory == null) { // Fallback for safety
                directory = System.getProperty("user.home");
            }
        } else {
            // Default behavior when the list is empty
            baseName = "merged";
            directory = System.getProperty("user.home");
        }

        // Get the selected format and construct the final path
        String format = (String) formatComboBox.getSelectedItem();
        Path outputPath = Paths.get(directory, baseName + "." + format);
        outputField.setText(outputPath.toString());
    }

    /**
     * Handles the '...' (Browse) action for the output file. Opens a save dialog.
     * @param e The action event (unused).
     */
    private void browseForOutput(ActionEvent e) {
        final JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Merged Video As...");
        chooser.setSelectedFile(new File(outputField.getText())); // Pre-populate with current path

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            outputField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    /**
     * Handles the 'Start Merging' action. It validates input and then starts the merge
     * operation on a background thread using a {@link SwingWorker}.
     * @param e The action event (unused).
     */
    private void mergeClips(ActionEvent e) {
        // 1. Validate input
        if (listModel.getSize() < 2) {
            JOptionPane.showMessageDialog(this, "Please add at least two clips to merge.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 2. Prepare for the long-running task
        progressListener.clearLog();
        final List<String> clipPaths = Collections.list(listModel.elements());
        final String outputFile = outputField.getText().trim();

        // Disable the button to prevent multiple concurrent operations
        actionButton.setEnabled(false);

        // 3. Execute the task on a background thread to avoid freezing the UI
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                progressListener.onProgress("Starting video merge operation...");
                videoClipsMerger.execute(clipPaths, outputFile, progressListener);
                return null; // This worker does not return a result
            }

            @Override
            protected void done() {
                // This block runs on the Event Dispatch Thread (EDT) after doInBackground completes
                try {
                    get(); // Call get() to propagate exceptions from the background thread
                } catch (Exception ex) {
                    progressListener.onError("An unexpected error occurred during merge: " + ex.getMessage());
                } finally {
                    // ALWAYS re-enable the button, even if an error occurred
                    actionButton.setEnabled(true);
                }
            }
        }.execute();
    }
}