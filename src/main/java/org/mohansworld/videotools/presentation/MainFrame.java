package org.mohansworld.videotools.presentation;

import net.miginfocom.swing.MigLayout;
import org.mohansworld.videotools.application.FfVideoProcessor;
import org.mohansworld.videotools.application.MkvPropertyEditor;
import org.mohansworld.videotools.application.MkvTrackRemover;
import org.mohansworld.videotools.application.MkvVideoProcessor;
import org.mohansworld.videotools.infrastructure.FfVideoProcessBuilder;
import org.mohansworld.videotools.infrastructure.MkvVideoProcessBuilder;

import javax.swing.*;

/**
 * The main application window for the MJ Video Tools application.
 * This class is responsible for setting up the main frame, initializing services,
 * and assembling the user interface components.
 */
public class MainFrame extends JFrame {

    private static final int FRAME_WIDTH = 900;
    private static final int FRAME_HEIGHT = 800;
    private static final int LOG_PANEL_HEIGHT = 500;

    public MainFrame() {
        configureFrame();
        initComponents();
    }

    /**
     * Configures the main JFrame properties like title, size, and layout.
     */
    private void configureFrame() {
        setTitle("MJ Video Tools");
        setSize(FRAME_WIDTH, FRAME_HEIGHT);
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // "insets 0" removes padding around the container for a tighter fit.
        setLayout(new MigLayout("insets 0, fill", "[grow]", "[grow][" + LOG_PANEL_HEIGHT + "]"));
    }

    /**
     * Initializes and wires up all application components.
     * This method acts as the "Composition Root", creating and connecting all dependencies and UI panels.
     */
    private void initComponents() {
        // --- Dependency Injection Setup ---
        // Infrastructure Layer
        FfVideoProcessor ffVideoProcessor = new FfVideoProcessBuilder();
        MkvVideoProcessor mkvVideoProcessor = new MkvVideoProcessBuilder();

        // Application Layer (Use Cases)
        MkvPropertyEditor mkvPropertyEditor = new MkvPropertyEditor(mkvVideoProcessor);
        MkvTrackRemover mkvTrackRemover = new MkvTrackRemover(mkvVideoProcessor);

        // --- UI Construction ---
        LogPanel logPanel = new LogPanel();
        JTabbedPane tabbedPane = createTabbedPane(ffVideoProcessor, mkvPropertyEditor, mkvTrackRemover, logPanel);

        // Add final components to the frame
        add(tabbedPane, "grow, wrap");
        add(logPanel, "grow");
    }

    /**
     * Creates and populates the JTabbedPane with all the feature panels.
     *
     * @param ffProc          The processor for FFmpeg-based operations.
     * @param mkvEditor       The service for editing MKV properties.
     * @param mkvRemover      The service for removing MKV tracks.
     * @param logPanel        The shared panel for displaying log output.
     * @return A fully populated JTabbedPane.
     */
    private JTabbedPane createTabbedPane(FfVideoProcessor ffProc, MkvPropertyEditor mkvEditor, MkvTrackRemover mkvRemover, LogPanel logPanel) {
        JTabbedPane tabbedPane = new JTabbedPane();

        tabbedPane.addTab("Convert", new VideoConvertPanel(ffProc, logPanel));
        tabbedPane.addTab("Split", new VideoSplitPanel(ffProc, logPanel));
        tabbedPane.addTab("Merge", new VideoClipsMergePanel(ffProc, logPanel));
        tabbedPane.addTab("Extract Audio", new AudioExtractPanel(ffProc, logPanel));
        tabbedPane.addTab("Extract Subtitles", new SubtitleExtractPanel(ffProc, logPanel));
        tabbedPane.addTab("MKV Track Remover", new MkvTrackRemovePanel(mkvEditor, mkvRemover, logPanel));
        tabbedPane.addTab("MKV Property Editor", new MkvPropertyEditPanel(mkvEditor, logPanel));

        return tabbedPane;
    }
}