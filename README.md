# MJ Video Tools

**MJ Video Tools** is a modern, cross-platform desktop application for easy and efficient video manipulation. Built with Java and powered by the robust `ffmpeg`,`ffprobe`,`mkvmerge` and `mkvpropedit` engines, it offers a clean, intuitive interface for common video editing tasks.

The application is designed following **Clean Architecture** principles, ensuring a separation of concerns, maintainability, and testability.

## Features

-   **Modern UI**: A sleek, dark-themed interface built with the FlatLaf library.
-   **Video Conversion**: Convert videos between popular formats (`MP4`, `MKV`, `MOV`) while preserving quality.
-   **Video Splitting**: Split a large video file into smaller clips of a specified duration.
-   **Video Merging**: Combine multiple video clips of the same format into a single video file.
-   **Subtitle Extraction**: Extract all subtitle tracks from a video into separate `.srt` files.
-   **Audio Extraction**: Extract all audio tracks from a video into high-quality audio files (`MP3`, `AAC`, `WAV`).
-   **Property Editor**: 
    - Load an MKV file to view its global title and track properties.
    - Edit the file title, track names, and toggle the 'enabled' and 'default' flags for each track.
    - Update the properties in-place without re-muxing the entire file.
-   **Track Remover**:
    - Analyze an MKV file to see a list of all its video, audio, and subtitle tracks.
    - Select which tracks to keep.
    - Create a new, clean MKV file containing only the selected tracks.
-   **Live Command Log**: See the exact `ffmpeg`/`ffprobe`/`mkvmerge`/`mkvpropedit` commands being executed for each operation.

## Prerequisites

Before running MJ Video Tools, you must have the following software installed on your system and available in your system's `PATH`:

1.  **Java Development Kit (JDK) 21 or newer**.
2.  **Apache Maven** (for building from source).
3.  **FFmpeg and FFprobe**: They are included for Windows and Linux environments.
4.  **MKVToolNix**: The application relies on `mkvmerge` and `mkvpropedit` command-line tools.
    - mkvmerge.exe and mkvpropedit.exe for Windows are included, 
    - Download and install from the official [MKVToolNix website](https://mkvtoolnix.download/) for linux environment.
    - **Crucially, ensure the MKVToolNix installation directory is added to your system's PATH environment variable**, so the commands can be run from any terminal.

To verify your installation, open a terminal or command prompt and run:
```sh
java -version
mkvmerge -version
mkvpropedit -version
```

## How to Build and Run

1.  **Clone the repository:**
    ```sh
    git clone <repository_url>
    cd mjvideotools
    ```

2.  **Build the project using Maven:**
    This command will compile the source code, run tests, and package the application into a single executable JAR file in the `target/` directory.
    ```sh
    mvn clean package
    ```

3.  **Run the application:**
    Navigate to the `target/` directory and execute the JAR file.
    ```sh
    java -jar target/mjvideotools-1.0-SNAPSHOT-jar-with-dependencies.jar
    ```

## Architecture

The application strictly follows Clean Architecture principles to separate responsibilities into distinct layers:

-   **Domain**: The core of the application. Contains the business entities and rules, with no dependencies on any other layer.
    -   `VideoFormat.java`, `AudioFormat.java`, `MkvPropertyInfo`
-   **Application**: Orchestrates the use cases of the application. It depends on the Domain layer but not on the UI or Infrastructure.
    -   `FfVideoProcessor.java` is an abstraction (interface) over the `ffmpeg`/`ffprobe` command execution.
    -   `MkvVideoProcessor.java` is an abstraction (interface) over the `mkvmerge`/`mkvpropedit` command execution.
-   **Infrastructure**: The outermost layer for external concerns. It implements the abstractions defined in the Application layer.
    -   `FfVideoProcessBuilder.java`, `MkvVideoProcessBuilder.java` are the concrete implementations that execute command-line processes.
-   **Presentation**: The user interface (UI) layer. It is built with Java Swing and depends only on the Application layer to execute tasks and display results.
    -   `MainFrame.java`, `*Panel.java` classes create the GUI.

This layered approach makes the application highly modular, easier to test (by mocking infrastructure), and flexible for future changes.