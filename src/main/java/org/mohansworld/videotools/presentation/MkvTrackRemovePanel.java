package org.mohansworld.videotools.presentation;

import net.miginfocom.swing.MigLayout;
import org.mohansworld.videotools.application.MkvPropertyEditor;
import org.mohansworld.videotools.application.MkvTrackRemover;
import org.mohansworld.videotools.application.ProgressListener;
import org.mohansworld.videotools.domain.MkvPropertyInfo;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A JPanel for selecting an MKV file, viewing its tracks,
 * and removing selected audio and subtitle tracks.
 * The panel interacts with asynchronous services to keep the UI responsive.
 */
public class MkvTrackRemovePanel extends JPanel {

    //<editor-fold desc="Class Fields">
    // --- Injected application services for business logic ---
    private final MkvPropertyEditor mkvPropertyEditor;
    private final MkvTrackRemover mkvTrackRemover;
    private final ProgressListener progressListener;

    // --- UI Components ---
    // REFACTOR 1 (CORRECTED): Initialize final fields at declaration.
    // This satisfies the compiler's requirement for final fields and is a
    // common, clean pattern for Swing components.
    private final JTextField inputFileField = new JTextField();
    private final JTextField outputFileField = new JTextField();
    private final JPanel tracksPanel = new JPanel(new MigLayout("wrap 1, fillx"));
    private final JButton actionButton = new JButton("Remove Tracks");
    private final JButton browseSourceButton = new JButton("Browse...");
    private final JButton browseDestButton = new JButton("Browse...");
    private final JButton clearAllButton = new JButton("Clear All");

    // --- State Management ---
    /**
     * Stores references to the dynamically created track checkboxes
     * to easily access them later for processing.
     */
    private final List<JCheckBox> trackCheckBoxes = new ArrayList<>();
    /**
     * A single, reusable file chooser improves performance and user experience
     * by remembering the last visited directory. Lazily initialized.
     */
    private JFileChooser mkvFileChooser;
    //</editor-fold>

    /**
     * Constructs the panel and wires up its components and dependencies.
     *
     * @param mkvPropertyEditor  Service to analyze MKV file properties.
     * @param mkvTrackRemover    Service to perform the track removal.
     * @param progressListener   Listener to report progress to another UI component.
     */
    public MkvTrackRemovePanel(MkvPropertyEditor mkvPropertyEditor, MkvTrackRemover mkvTrackRemover, ProgressListener progressListener) {
        this.mkvPropertyEditor = mkvPropertyEditor;
        this.mkvTrackRemover = mkvTrackRemover;
        this.progressListener = progressListener;

        // The constructor's role is now to orchestrate the setup.
        initListeners();
        layoutComponents();

        updateButtonStates();
    }

    /**
     * Sets up the layout of the panel and adds components to it.
     */
    private void layoutComponents() {
        setLayout(new MigLayout(
                "fillx, insets 10", // Layout constraints: fill horizontally, 10px margins
                "[grow, fill]",     // Column constraints: one column that grows and fills
                "[]10[]15[grow, fill]" // Row constraints: two fixed rows, one growing row, with gaps
        ));

        // --- File selection panel ---
        JPanel filePanel = new JPanel(new MigLayout("fillx, insets 0", "[][grow, fill][]"));
        filePanel.add(new JLabel("Source MKV:"));
        filePanel.add(inputFileField, "growx");
        filePanel.add(browseSourceButton, "wrap");
        filePanel.add(new JLabel("Destination MKV:"));
        filePanel.add(outputFileField, "growx");
        filePanel.add(browseDestButton, "wrap");

        // --- Action buttons panel ---
        JPanel actionButtonsPanel = new JPanel(new MigLayout("insets 0"));
        actionButtonsPanel.add(actionButton, "w 150!"); // Fixed width for consistent look
        actionButtonsPanel.add(clearAllButton, "w 150!");

        // --- Tracks panel with scroll pane ---
        JScrollPane tracksScrollPane = new JScrollPane(tracksPanel);
        tracksScrollPane.setBorder(BorderFactory.createTitledBorder("Tracks to Keep (Uncheck to remove)"));

        // --- Add all sub-panels to the main panel ---
        add(filePanel, "wrap");
        add(actionButtonsPanel, "wrap");
        add(tracksScrollPane, "growx, growy"); // Make scroll pane grow in both directions
    }

    /**
     * Registers all event listeners for the interactive components.
     */
    private void initListeners() {
        browseSourceButton.addActionListener(e -> chooseSourceFile());
        browseDestButton.addActionListener(e -> chooseDestFile());
        actionButton.addActionListener(e -> removeTracks());
        clearAllButton.addActionListener(e -> clearPanel());
    }

    /**
     * Resets the panel to its initial state.
     */
    public void clearPanel() {
        inputFileField.setText("");
        outputFileField.setText("");
        tracksPanel.removeAll();
        trackCheckBoxes.clear();
        // Required to reflect the removal of components
        tracksPanel.revalidate();
        tracksPanel.repaint();
        progressListener.clearLog();
        updateButtonStates();
    }

    /**
     * Enables or disables the main action button based on the current state.
     * The button should only be active if tracks have been loaded.
     */
    private void updateButtonStates() {
        boolean tracksLoaded = !trackCheckBoxes.isEmpty();
        actionButton.setEnabled(tracksLoaded);
    }

    /**
     * Opens a file chooser for the user to select a source MKV file.
     * After selection, it automatically loads the file's tracks.
     */
    private void chooseSourceFile() {
        if (getMkvFileChooser().showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = getMkvFileChooser().getSelectedFile();
            String path = selectedFile.getAbsolutePath();
            inputFileField.setText(path);

            // Auto-populate destination if it's empty
            if (outputFileField.getText().isBlank()) {
                String newPath = path.substring(0, path.lastIndexOf('.')) + "-new.mkv";
                outputFileField.setText(newPath);
            }
            loadTracks();
        }
    }

    /**
     * Opens a file chooser for the user to select a destination MKV file.
     * Ensures the file has a .mkv extension.
     */
    private void chooseDestFile() {
        JFileChooser chooser = getMkvFileChooser();
        chooser.setDialogTitle("Save As"); // Customize for the save action
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            // A more robust way to ensure the file has the correct extension.
            if (!path.toLowerCase().endsWith(".mkv")) {
                path += ".mkv";
            }
            outputFileField.setText(path);
        }
    }

    /**
     * Consolidates JFileChooser creation.
     * Lazily initializes and reuses a single JFileChooser instance.
     *
     * @return A configured JFileChooser for MKV files.
     */
    private JFileChooser getMkvFileChooser() {
        if (mkvFileChooser == null) {
            mkvFileChooser = new JFileChooser();
            mkvFileChooser.setFileFilter(new FileNameExtensionFilter("MKV Videos", "mkv"));
        }
        return mkvFileChooser;
    }

    /**
     * Initiates the asynchronous process of reading tracks from the source file.
     */
    private void loadTracks() {
        String sourcePath = inputFileField.getText();
        if (sourcePath.isBlank() || !new File(sourcePath).exists()) {
            JOptionPane.showMessageDialog(this, "Please select a valid source file.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        mkvPropertyEditor.getProperties(sourcePath, progressListener)
                .thenAccept(this::displayTracks)
                .exceptionally(ex -> {
                    handleAsyncError(ex, "Failed to analyze file");
                    return null; // Required for exceptionally block
                });
    }

    /**
     * Populates the UI with checkboxes for each track found in the MKV file.
     * This method is designed to be called on the Event Dispatch Thread (EDT).
     *
     * @param info The properties of the MKV file, including its tracks.
     */
    private void displayTracks(MkvPropertyInfo info) {
        SwingUtilities.invokeLater(() -> {
            tracksPanel.removeAll();
            trackCheckBoxes.clear();

            for (MkvPropertyInfo.Track track : info.getTracks()) {
                String label = String.format("ID %d: %s (%s) - '%s' [%s]",
                        track.getId(),
                        track.getType().getDisplayName(),
                        track.getCodec(),
                        track.getTrackName().isEmpty() ? "No Name" : track.getTrackName(),
                        track.getLanguage());

                JCheckBox checkBox = new JCheckBox(label, true);
                checkBox.putClientProperty("trackInfo", track);

                if (track.getType() == MkvPropertyInfo.TrackType.VIDEO) {
                    checkBox.setEnabled(false);
                    checkBox.setToolTipText("Video tracks are always kept and cannot be removed.");
                }

                trackCheckBoxes.add(checkBox);
                tracksPanel.add(checkBox, "growx");
            }
            tracksPanel.revalidate();
            tracksPanel.repaint();
            updateButtonStates();
        });
    }

    /**
     * Gathers user selections and initiates the asynchronous track removal process.
     */
    private void removeTracks() {
        String sourcePath = inputFileField.getText();
        String destPath = outputFileField.getText();

        if (sourcePath.isBlank() || destPath.isBlank()) {
            JOptionPane.showMessageDialog(this, "Source and destination paths are required.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (sourcePath.equalsIgnoreCase(destPath)) {
            JOptionPane.showMessageDialog(this, "Source and destination files cannot be the same.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<Integer> videoIdsToKeep = new ArrayList<>();
        List<Integer> audioIdsToKeep = new ArrayList<>();
        List<Integer> subtitleIdsToKeep = new ArrayList<>();

        for (JCheckBox checkBox : trackCheckBoxes) {
            if (checkBox.isSelected()) {
                MkvPropertyInfo.Track track = (MkvPropertyInfo.Track) checkBox.getClientProperty("trackInfo");
                switch (track.getType()) {
                    case VIDEO -> videoIdsToKeep.add(track.getId());
                    case AUDIO -> audioIdsToKeep.add(track.getId());
                    case SUBTITLES -> subtitleIdsToKeep.add(track.getId());
                }
            }
        }

        MkvTrackRemover.TrackRemovalOptions options = new MkvTrackRemover.TrackRemovalOptions(
                sourcePath, destPath, videoIdsToKeep, audioIdsToKeep, subtitleIdsToKeep
        );

        Consumer<Void> onSuccess = v -> {
            progressListener.onComplete("File created successfully!");
            updateButtonStates();
        };

        mkvTrackRemover.removeTracks(options, progressListener)
                .thenAcceptAsync(onSuccess, SwingUtilities::invokeLater)
                .exceptionally(ex -> {
                    handleAsyncError(ex, "Failed to create new file");
                    return null;
                });
    }

    /**
     * Centralized error handler for CompletableFuture exceptions.
     * This reduces code duplication and ensures all errors are handled consistently
     * on the Event Dispatch Thread.
     *
     * @param throwable The exception thrown by the future.
     * @param messagePrefix A user-friendly message to display before the exception details.
     */
    private void handleAsyncError(Throwable throwable, String messagePrefix) {
        Throwable cause = (throwable.getCause() != null) ? throwable.getCause() : throwable;
        String errorMessage = String.format("%s: %s", messagePrefix, cause.getMessage());
        SwingUtilities.invokeLater(() -> {
            progressListener.onError(errorMessage);
            updateButtonStates();
        });
    }
}