package org.mohansworld.videotools.presentation;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;

/**
 * A panel displaying "About" information for the MJ Video Tools application.
 * Displays a formatted, scrollable text area with centered title, version,
 * justified description with padding, and developer attribution.
 * Uses MigLayout for flexible, responsive layouting.
 */
public class AboutPanel extends JPanel {

    // === Font & Layout Constants ===
    private static final String FONT_NAME = "SansSerif";
    private static final int FONT_SIZE = 14;           // Base font size for body text
    private static final int TITLE_FONT_SIZE = 18;     // Larger font for title
    private static final int MARGIN_SIZE = 10;         // Inner margin around text pane
    private static final int SECTION_SPACING = 10;     // Vertical space between sections
    private static final int DESC_PADDING = 100;       // Left/right padding for centered description

    /**
     * Constructs the AboutPanel and initializes its UI components.
     * Sets up a MigLayout container and adds a scrollable text area with formatted content.
     */
    public AboutPanel() {
        initializeLayout();
        JTextPane aboutArea = createAboutArea();
        add(createScrollPane(aboutArea), "grow"); // Grow to fill available space
    }

    /**
     * Initializes the layout manager for this panel using MigLayout.
     * "fill" constraint ensures the child component expands to fill the entire panel.
     */
    private void initializeLayout() {
        setLayout(new net.miginfocom.swing.MigLayout("fill"));
    }

    /**
     * Creates and configures a JTextPane containing the formatted "About" content.
     * Returns a non-editable text pane with styled text sections: Title, Version, Description, Developer.
     *
     * @return a fully configured JTextPane with formatted content
     */
    private JTextPane createAboutArea() {
        JTextPane textPane = new JTextPane();
        textPane.setEditable(false); // Prevent user editing
        textPane.setMargin(new Insets(MARGIN_SIZE, MARGIN_SIZE, MARGIN_SIZE, MARGIN_SIZE));
        textPane.setFont(new Font(FONT_NAME, Font.PLAIN, FONT_SIZE));

        StyledDocument doc = textPane.getStyledDocument();

        // --- Title ---
        appendStyledText(doc, "MJ Video Tools" + "\n", createTitleStyle());

        // --- Version ---
        appendStyledText(doc, "Version 1.0" + "\n", createNormalCenteredStyle());

        // --- Developer ---
        appendStyledText(doc, "Developed by Mohan John" + "\n", createNormalCenteredStyle());

        // --- Description ---
        // Use String.indent() to normalize indentation from the multiline string literal
        String description = """
                A simple, cross-platform desktop app for working with video files.
                It uses powerful tools like FFmpeg and MKVToolnix under the hood,
                wrapped in a clean and easy-to-use interface.
                """.indent(0); // Remove leading indentation from triple quotes
        appendStyledText(doc, description + "\n", createNormalCenteredWithPaddingStyle());

        return textPane;
    }

    /**
     * Helper method to append styled text to a StyledDocument.
     * Automatically applies the given attributes and ensures paragraph-level styling.
     *
     * @param doc   the document to append to
     * @param text  the text to insert
     * @param attr  the style attributes to apply
     */
    private void appendStyledText(StyledDocument doc, String text, AttributeSet attr) {
        try {
            int offset = doc.getLength(); // Position at end of document
            doc.insertString(offset, text, attr);

            // Apply attributes to the entire paragraph where text was inserted
            // Note: We use 'text.length()' because we're inserting one paragraph per call
            doc.setParagraphAttributes(offset, text.length(), attr, false);
        } catch (BadLocationException e) {
            System.err.println("Error appending styled text: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // === Style Factory Methods ===

    /**
     * Creates a style for the main title: bold, larger font, centered alignment.
     * Adds vertical space below for separation.
     *
     * @return an AttributeSet configured for title styling
     */
    private AttributeSet createTitleStyle() {
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setAlignment(attr, StyleConstants.ALIGN_CENTER);
        StyleConstants.setBold(attr, true);
        StyleConstants.setFontSize(attr, TITLE_FONT_SIZE);
        StyleConstants.setSpaceBelow(attr, SECTION_SPACING);
        return attr;
    }

    /**
     * Creates a style for centered normal text (e.g., version, developer).
     * Includes vertical spacing below for section separation.
     *
     * @return an AttributeSet configured for centered normal text
     */
    private AttributeSet createNormalCenteredStyle() {
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setAlignment(attr, StyleConstants.ALIGN_CENTER);
        StyleConstants.setFontSize(attr, FONT_SIZE);
        StyleConstants.setSpaceBelow(attr, SECTION_SPACING);
        return attr;
    }

    /**
     * Creates a style for centered text with horizontal padding on both sides.
     * Used for the description paragraph to improve readability and visual balance.
     *
     * @return an AttributeSet configured for justified text with side padding
     */
    private AttributeSet createNormalCenteredWithPaddingStyle() {
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setAlignment(attr, StyleConstants.ALIGN_CENTER);
        StyleConstants.setFontSize(attr, FONT_SIZE);
        StyleConstants.setLeftIndent(attr, DESC_PADDING);
        StyleConstants.setRightIndent(attr, DESC_PADDING);
        return attr;
    }

    /**
     * Wraps a JTextComponent in a JScrollPane to enable scrolling when content overflows.
     *
     * @param textComponent the text component to wrap (e.g., JTextPane)
     * @return a JScrollPane containing the component with default scroll policies
     */
    private JScrollPane createScrollPane(JTextComponent textComponent) {
        return new JScrollPane(textComponent);
    }
}