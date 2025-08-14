package org.mohansworld.videotools;

import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import org.mohansworld.videotools.presentation.MainFrame;

import javax.swing.*;
import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The main entry point for the Video Tools application.
 * <p>
 * This class is responsible for initializing the application's look and feel,
 * setting global UI properties like the default font, and launching the main window.
 * This class is not meant to be instantiated.
 */
public final class MainApp {

    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());
    private static final int DEFAULT_FONT_SIZE = 12;

    private MainApp() {
        // This class should not be instantiated.
    }

    /**
     * Initializes the global user interface settings for the application.
     * This includes setting the Look and Feel and the default font.
     */
    private static void initializeUI() {
        // 1. Set the modern dark Look and Feel.
        // Using a try-catch block makes the app more robust. If FlatLaf fails,
        // it will fall back to the default Swing L&F instead of crashing.
        try {
            FlatMacDarkLaf.setup();
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to initialize FlatLaf Look and Feel.", ex);
            // The application will continue with the default L&F.
        }

        // 2. Set the default font for the entire application.
        // FlatLaf provides a simple and efficient way to set the default font
        // without iterating through all UIManager keys.
        Font defaultFont = new Font(Font.SANS_SERIF, Font.PLAIN, DEFAULT_FONT_SIZE);
        UIManager.put("defaultFont", defaultFont);
    }

    /**
     * The main method that starts the application.
     *
     * @param args Command-line arguments (not used).
     */
    public static void main(String[] args) {
        // It's good practice to set up the UI Look and Feel and other properties
        // before any Swing components are created.
        initializeUI();

        // Schedule the creation and display of the main frame on the Event Dispatch Thread (EDT).
        // This is the standard and safe way to start a Swing application.
        SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame();
            mainFrame.setVisible(true);
        });
    }

}