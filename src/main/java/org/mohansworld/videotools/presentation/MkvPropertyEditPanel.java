package org.mohansworld.videotools.presentation;

import net.miginfocom.swing.MigLayout;
import org.mohansworld.videotools.application.MkvPropertyEditor;
import org.mohansworld.videotools.application.ProgressListener;
import org.mohansworld.videotools.domain.MkvPropertyInfo;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A Swing JPanel for viewing and editing properties of an MKV video file.
 * It allows users to select a file, view its title and track properties in a table,
 * modify them, and apply the changes.
 * The panel communicates with an {@link MkvPropertyEditor} for business logic
 * and uses a {@link ProgressListener} to report progress and logs.
 */
public class MkvPropertyEditPanel extends JPanel {

    // --- Business Logic Dependencies ---
    private final MkvPropertyEditor mkvPropertyEditor;
    private final ProgressListener progressListener;

    // --- UI Components ---
    private JTextField inputFileField;
    private JTextField titleField;
    private JTable propertiesTable;
    private DefaultTableModel tableModel;
    private JButton actionButton;

    // --- Constants for Table Columns ---
    private static final int COL_SELECTOR = 0;
    private static final int COL_TYPE = 1;
    private static final int COL_CODEC = 2;
    private static final int COL_TRACK_NAME = 3;
    private static final int COL_DEFAULT = 4;
    private static final int COL_ENABLED = 5;
    private static final String[] TABLE_COLUMN_NAMES = {"Selector", "Type", "Codec", "Track Name", "Default", "Enabled"};

    /**
     * Constructs the main panel for editing MKV properties.
     *
     * @param mkvPropertyEditor  The application service for handling MKV file operations.
     * @param progressListener   The listener to report progress and log messages.
     */
    public MkvPropertyEditPanel(final MkvPropertyEditor mkvPropertyEditor, final ProgressListener progressListener) {
        this.mkvPropertyEditor = mkvPropertyEditor;
        this.progressListener = progressListener;

        // The main layout for the entire panel
        setLayout(new MigLayout("fillx, insets 10", "[grow, fill]", "[]15[]15[grow, fill]"));

        // Decompose panel creation into logical units
        add(createFilePanel(), "wrap");
        add(createPropertiesPanel(), "grow, wrap");

        updateButtonStates();
    }

    /**
     * Creates the top panel containing the file selection input and action buttons.
     *
     * @return The configured file selection JPanel.
     */
    private JPanel createFilePanel() {
        JPanel filePanel = new JPanel(new MigLayout("insets 10, fillx", "[]0[grow, fill]", "[]"));

        filePanel.add(new JLabel("MKV File: "));
        inputFileField = new JTextField();
        filePanel.add(inputFileField, "split 2, growx");

        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> chooseFile());
        filePanel.add(browseButton, "wrap");

        actionButton = new JButton("Update Properties");
        actionButton.addActionListener(e -> updateProperties());

        JButton clearAllButton = new JButton("Clear All");
        clearAllButton.addActionListener(e -> clearPanel());

        // Add action buttons on a new row, spanning across the panel
        filePanel.add(actionButton, "cell 0 2, spanx, split 2, gaptop 10, w 150!");
        filePanel.add(clearAllButton, "w 150!");

        return filePanel;
    }

    /**
     * Creates the main content panel which includes the file title and the properties table.
     *
     * @return The configured properties container JPanel.
     */
    private JPanel createPropertiesPanel() {
        JPanel propertiesContainerPanel = new JPanel(new MigLayout("fill, insets 5", "[grow, fill]", "[]10[grow, fill]"));
        propertiesContainerPanel.setBorder(BorderFactory.createTitledBorder("MKV Properties"));

        // --- Title Sub-panel ---
        JPanel titlePanel = new JPanel(new MigLayout("fillx, insets 0", "[][grow, fill]"));
        titlePanel.add(new JLabel("File Title:"));
        titleField = new JTextField();
        titlePanel.add(titleField, "growx");

        // --- Properties Table ---
        propertiesTable = createPropertiesTable();
        JScrollPane tableScrollPane = new JScrollPane(propertiesTable);

        // Add sub-panels to the container
        propertiesContainerPanel.add(titlePanel, "wrap");
        propertiesContainerPanel.add(tableScrollPane, "grow");

        return propertiesContainerPanel;
    }

    /**
     * Creates and configures the JTable for displaying track properties.
     *
     * @return The configured JTable.
     */
    private JTable createPropertiesTable() {
        // The table model is customized to handle boolean values automatically.
        tableModel = new DefaultTableModel(TABLE_COLUMN_NAMES, 0) {
            /**
             * Determines which cells are editable. Only track name, default, and enabled flags can be changed.
             */
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == COL_TRACK_NAME || column == COL_DEFAULT || column == COL_ENABLED;
            }

            /**
             * Informs the JTable of the data type for each column.
             * By returning Boolean.class, the table will automatically use a JCheckBox
             * for rendering and editing, simplifying the code significantly.
             */
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == COL_DEFAULT || columnIndex == COL_ENABLED) {
                    return Boolean.class;
                }
                return String.class;
            }
        };

        JTable table = new JTable(tableModel);
        table.setRowHeight(25);
        return table;
    }

    /**
     * Clears all input fields, the properties table, and the progress log.
     * Resets the panel to its initial state.
     */
    public void clearPanel() {
        // If a cell is being edited, cancel the edit before clearing the table
        if (propertiesTable.isEditing()) {
            propertiesTable.getCellEditor().cancelCellEditing();
        }
        inputFileField.setText("");
        titleField.setText("");
        tableModel.setRowCount(0);
        progressListener.clearLog();
        updateButtonStates();
    }

    /**
     * Enables or disables the "Update Properties" button based on whether
     * there is data in the properties table.
     */
    private void updateButtonStates() {
        final boolean hasData = tableModel.getRowCount() > 0;
        actionButton.setEnabled(hasData);
    }

    /**
     * Opens a file chooser to select an MKV file and triggers loading its properties.
     */
    private void chooseFile() {
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("MKV Videos", "mkv"));
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            inputFileField.setText(fileChooser.getSelectedFile().getAbsolutePath());
            loadProperties();
        }
    }

    /**
     * Initiates the asynchronous loading of properties from the selected MKV file.
     * Handles success and failure cases, updating the UI on the Event Dispatch Thread.
     */
    private void loadProperties() {
        final String filePath = inputFileField.getText();
        if (filePath.isBlank() || !new File(filePath).exists()) {
            JOptionPane.showMessageDialog(this, "Please select a valid MKV file first.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        clearPanel(); // Clear previous data before loading new data
        inputFileField.setText(filePath); // Restore file path after clearing
        progressListener.onProgress("Loading properties from: " + filePath);

        // Asynchronously get properties to avoid freezing the UI
        mkvPropertyEditor.getProperties(filePath, progressListener)
                .thenAccept(this::displayProperties) // On success, display them
                .exceptionally(ex -> { // On failure, show an error
                    SwingUtilities.invokeLater(() -> {
                        // Use a null-safe way to get the error message
                        String errorMessage = (ex.getCause() != null) ? ex.getCause().getMessage() : ex.getMessage();
                        progressListener.onError("Failed to load properties: " + errorMessage);
                    });
                    return null;
                });
    }



    /**
     * Populates the UI fields and table with the properties loaded from the file.
     * This method must be called on the Event Dispatch Thread.
     *
     * @param info The container for the loaded MKV properties.
     */
    private void displayProperties(final MkvPropertyInfo info) {
        // Ensure UI updates happen on the Event Dispatch Thread.
        SwingUtilities.invokeLater(() -> {
            titleField.setText(info.getTitle());
            tableModel.setRowCount(0); // Clear previous data

            for (final MkvPropertyInfo.Track track : info.getTracks()) {
                tableModel.addRow(new Object[]{
                        track.getSelector(),
                        track.getType().getDisplayName(),
                        track.getCodec(),
                        track.getTrackName(),
                        track.isDefaultTrack(), // This boolean is automatically handled by the JTable
                        track.isEnabledTrack()  // This boolean is automatically handled by the JTable
                });
            }
            updateButtonStates();
        });
    }

    /**
     * Gathers the modified data from the UI and sends it to the MkvPropertyEditor
     * to update the file.
     */
    private void updateProperties() {
        // Ensure any active cell editing is committed to the model before reading values.
        final TableCellEditor cellEditor = propertiesTable.getCellEditor();
        if (cellEditor != null) {
            cellEditor.stopCellEditing();
        }

        final String filePath = inputFileField.getText();
        final String newTitle = titleField.getText();
        final List<MkvPropertyEditor.TrackUpdateInfo> trackUpdates = new ArrayList<>();

        // Iterate through the table model to collect user changes.
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            final String selector = (String) tableModel.getValueAt(i, COL_SELECTOR);
            final String name = (String) tableModel.getValueAt(i, COL_TRACK_NAME);

            // Retrieve boolean values directly from the model, thanks to getColumnClass().
            final Boolean isDefaultBool = (Boolean) tableModel.getValueAt(i, COL_DEFAULT);
            final Boolean isEnabledBool = (Boolean) tableModel.getValueAt(i, COL_ENABLED);

            // Convert booleans back to the "1" or "0" string format required by mkvpropedit.
            final String isDefaultStr = isDefaultBool ? "1" : "0";
            final String isEnabledStr = isEnabledBool ? "1" : "0";

            trackUpdates.add(new MkvPropertyEditor.TrackUpdateInfo(selector, name, isEnabledStr, isDefaultStr));
        }

        progressListener.onProgress("Starting property update for: " + filePath);
        // Asynchronously update properties to avoid freezing the UI
        mkvPropertyEditor.updateProperties(filePath, newTitle, trackUpdates, progressListener)
                .thenRun(() -> SwingUtilities.invokeLater(() -> progressListener.onComplete("Update completed successfully.")))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        // Use a null-safe way to get the error message
                        String errorMessage = (ex.getCause() != null) ? ex.getCause().getMessage() : ex.getMessage();
                        progressListener.onError("Failed to update properties: " + errorMessage);
                    });
                    return null;
                });
    }
}