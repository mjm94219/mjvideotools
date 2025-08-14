package org.mohansworld.videotools.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link VideoClipsMerger} class.
 * These tests use mocks for all external dependencies, including the file system (via Files)
 * and external processes (via FfVideoProcessor), to test the logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class VideoClipsMergerTest {

    private static final String DUMMY_OUTPUT_FILE = "/path/to/merged.mp4";
    private static final List<String> DUMMY_CLIPS = List.of("/path/to/clip1.mp4", "/path/to/clip2.mp4");

    @Mock
    private FfVideoProcessor mockVideoProcessor;
    @Mock
    private ProgressListener mockListener;
    @Captor
    private ArgumentCaptor<List<String>> commandCaptor;
    @InjectMocks
    private VideoClipsMerger videoClipsMerger;

    @Test
    void execute_withValidClips_createsTempFileAndExecutesFfmpeg() {
        // Arrange
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class);
             MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {

            Path mockPath = mock(Path.class);
            StringWriter stringWriter = new StringWriter();
            BufferedWriter bufferedWriter = new BufferedWriter(stringWriter);

            // --- FINAL FIX IS HERE: Use eq() for the specific Charset ---
            mockedFiles.when(() -> Files.newBufferedWriter(any(Path.class), eq(StandardCharsets.UTF_8))).thenReturn(bufferedWriter);

            mockedFileUtils.when(() -> FileUtils.getToolPath(FileUtils.Tool.FFMPEG)).thenReturn("ffmpeg");
            mockedFiles.when(() -> Files.createTempFile(anyString(), anyString())).thenReturn(mockPath);

            when(mockPath.toString()).thenReturn("/tmp/tempfile.txt");
            when(mockVideoProcessor.executeFfmpeg(any(), any())).thenReturn(CompletableFuture.completedFuture(0));

            // Act
            videoClipsMerger.execute(DUMMY_CLIPS, DUMMY_OUTPUT_FILE, mockListener);

            // Assert
            String expectedContent = "file '/path/to/clip1.mp4'" + System.lineSeparator() +
                    "file '/path/to/clip2.mp4'" + System.lineSeparator();
            assertEquals(expectedContent, stringWriter.toString());

            verify(mockVideoProcessor, timeout(100)).executeFfmpeg(commandCaptor.capture(), eq(mockListener));
            List<String> actualCommand = commandCaptor.getValue();
            assertTrue(actualCommand.containsAll(List.of("-f", "concat", "-i", "/tmp/tempfile.txt", "-c", "copy")));

            mockedFiles.verify(() -> Files.deleteIfExists(mockPath), timeout(100));
            verify(mockListener, never()).onError(anyString());
        }
    }

    @Test
    void execute_withEmptyClipList_reportsErrorAndStops() {
        // Act
        videoClipsMerger.execute(List.of(), DUMMY_OUTPUT_FILE, mockListener);
        // Assert
        verify(mockListener).onError("No video clips provided to merge.");
        verify(mockVideoProcessor, never()).executeFfmpeg(any(), any());
    }

    @Test
    void execute_whenTempFileCreationFails_reportsError() {
        // Arrange
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            IOException ioException = new IOException("Disk full");
            mockedFiles.when(() -> Files.createTempFile(anyString(), anyString())).thenThrow(ioException);
            // Act
            videoClipsMerger.execute(DUMMY_CLIPS, DUMMY_OUTPUT_FILE, mockListener);
            // Assert
            verify(mockListener).onError("Failed to create temporary file for merging: Disk full");
            verify(mockVideoProcessor, never()).executeFfmpeg(any(), any());
        }
    }

    @Test
    void execute_whenFfmpegFails_stillAttemptsToCleanUpTempFile() {
        // Arrange
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class);
             MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {

            Path mockPath = mock(Path.class);

            // --- FINAL FIX IS HERE: Use eq() for the specific Charset ---
            mockedFiles.when(() -> Files.newBufferedWriter(any(Path.class), eq(StandardCharsets.UTF_8)))
                    .thenReturn(new BufferedWriter(new StringWriter()));

            mockedFileUtils.when(() -> FileUtils.getToolPath(any())).thenReturn("ffmpeg");
            mockedFiles.when(() -> Files.createTempFile(anyString(), anyString())).thenReturn(mockPath);

            when(mockVideoProcessor.executeFfmpeg(any(), any()))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("ffmpeg exited with error")));

            // Act
            videoClipsMerger.execute(DUMMY_CLIPS, DUMMY_OUTPUT_FILE, mockListener);

            // Assert
            mockedFiles.verify(() -> Files.deleteIfExists(mockPath), timeout(100));
        }
    }

    @Test
    void execute_withSingleQuoteInPath_escapesPathCorrectlyInTempFile() {
        // Arrange
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class);
             MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {

            Path mockPath = mock(Path.class);
            StringWriter stringWriter = new StringWriter();

            // --- FINAL FIX IS HERE: Use eq() for the specific Charset ---
            mockedFiles.when(() -> Files.newBufferedWriter(any(Path.class), eq(StandardCharsets.UTF_8)))
                    .thenReturn(new BufferedWriter(stringWriter));

            mockedFileUtils.when(() -> FileUtils.getToolPath(any())).thenReturn("ffmpeg");
            mockedFiles.when(() -> Files.createTempFile(any(), any())).thenReturn(mockPath);
            when(mockVideoProcessor.executeFfmpeg(any(), any())).thenReturn(CompletableFuture.completedFuture(0));

            List<String> clipsWithQuote = List.of("my 'awesome' movie.mp4");

            // Act
            videoClipsMerger.execute(clipsWithQuote, DUMMY_OUTPUT_FILE, mockListener);

            // Assert
            String expectedContent = "file 'my '\\''awesome'\\'' movie.mp4'" + System.lineSeparator();
            assertEquals(expectedContent, stringWriter.toString());
        }
    }
}