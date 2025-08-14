package org.mohansworld.videotools.presentation;

import net.miginfocom.swing.MigLayout;
import org.mohansworld.videotools.application.ProgressListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A JPanel that displays log messages from a process.
 * It implements ProgressListener to receive updates and ensures all UI modifications
 * are performed safely on the Event Dispatch Thread (EDT).
 */
public class LogPanel extends JPanel implements ProgressListener {

    private static final String PANEL_TITLE = "Log Output";
    private static final String FONT_NAME = Font.MONOSPACED;
    private static final int FONT_SIZE = 12;
    private static final int MARGIN_SIZE = 5;

    private static final String MENU_ITEM_COPY = "Copy";
    private static final String MENU_ITEM_SELECT_ALL = "Select All";
    private static final String MENU_ITEM_CLEAR_LOG = "Clear Log";

    private static final String SUCCESS_PREFIX = "SUCCESS: ";
    private static final String ERROR_PREFIX = "ERROR: ";
    private static final String LINE_SEPARATOR = "\n";
    private static final String BLOCK_SEPARATOR = "\n\n";

    private final JTextArea logArea;

    /**
     * Constructs the LogPanel and initializes its UI components.
     */
    public LogPanel() {
        initializeLayout();
        this.logArea = createLogArea();
        add(createScrollPane(logArea), "grow");
        createRightClickContextMenu();
    }

    /**
     * Sets up the panel's layout and border.
     */
    private void initializeLayout() {
        setLayout(new MigLayout("fill", "[grow]", "[grow]"));
        setBorder(BorderFactory.createTitledBorder(PANEL_TITLE));
    }

    /**
     * Creates and configures the JTextArea for logging.
     * @return The configured JTextArea.
     */
    private JTextArea createLogArea() {
        final JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font(FONT_NAME, Font.PLAIN, FONT_SIZE));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setMargin(new Insets(MARGIN_SIZE, MARGIN_SIZE, MARGIN_SIZE, MARGIN_SIZE));
        return textArea;
    }

    /**
     * Wraps the given text area in a JScrollPane.
     * @param textArea The text area to wrap.
     * @return The configured JScrollPane.
     */
    private JScrollPane createScrollPane(JTextArea textArea) {
        return new JScrollPane(textArea);
    }

    // --- ProgressListener Implementation ---

    @Override
    public void onProgress(String message) {
        // CRITICAL: Ensure all UI updates happen on the Event Dispatch Thread (EDT).
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + LINE_SEPARATOR);
            // Auto-scroll to the bottom
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    @Override
    public void onComplete(String finalMessage) {
        SwingUtilities.invokeLater(() -> logArea.append(SUCCESS_PREFIX + finalMessage + BLOCK_SEPARATOR));
    }

    @Override
    public void onError(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(ERROR_PREFIX + message + BLOCK_SEPARATOR));
    }

    @Override
    public void clearLog() {
        SwingUtilities.invokeLater(() -> logArea.setText(""));
    }

    /**
     * Creates and attaches a right-click context menu to the log area
     * with "Copy", "Select All", and "Clear Log" actions.
     */
    private void createRightClickContextMenu() {
        final JPopupMenu popupMenu = new JPopupMenu();

        final JMenuItem copyItem = new JMenuItem(MENU_ITEM_COPY);
        copyItem.addActionListener(e -> logArea.copy());

        final JMenuItem selectAllItem = new JMenuItem(MENU_ITEM_SELECT_ALL);
        selectAllItem.addActionListener(e -> logArea.selectAll());

        final JMenuItem clearLogItem = new JMenuItem(MENU_ITEM_CLEAR_LOG);
        clearLogItem.addActionListener(e -> clearLog());

        popupMenu.add(copyItem);
        popupMenu.add(selectAllItem);
        popupMenu.addSeparator();
        popupMenu.add(clearLogItem);

        logArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    // Enable/disable menu items based on the current state of the log area.
                    boolean hasSelection = logArea.getSelectedText() != null;
                    boolean hasText = logArea.getDocument().getLength() > 0;

                    copyItem.setEnabled(hasSelection);
                    selectAllItem.setEnabled(hasText);
                    clearLogItem.setEnabled(hasText);

                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }
}