package org.mohansworld.videotools.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link SubtitleExtractor} class.
 * These tests verify the asynchronous workflow, command construction for ffprobe/ffmpeg,
 * JSON parsing logic, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class SubtitleExtractorTest {

    private static final String DUMMY_INPUT_FILE = "/path/to/video.mkv";
    private static final String DUMMY_BASE_NAME = "video";

    @Mock
    private FfVideoProcessor mockVideoProcessor;

    @Mock
    private ProgressListener mockListener;

    @Captor
    private ArgumentCaptor<List<String>> commandCaptor;

    @InjectMocks
    private SubtitleExtractor subtitleExtractor;

    /** Helper to generate ffprobe JSON output for testing. */
    private String generateMockFfprobeOutput(String... subtitles) {
        StringBuilder tracks = new StringBuilder();
        // Add a dummy video track for realism
        tracks.append("{\"index\": 0, \"codec_type\": \"video\"}");

        for (String subtitleInfo : subtitles) {
            tracks.append(",");
            String[] parts = subtitleInfo.split(":"); // e.g., "2:eng" or "3:und"
            int index = Integer.parseInt(parts[0]);
            String lang = parts[1];
            String tags = lang.equals("notags") ? "" : String.format(", \"tags\": { \"language\": \"%s\" }", lang);

            tracks.append(String.format(
                    "{\"index\": %d, \"codec_type\": \"subtitle\" %s}",
                    index, tags
            ));
        }
        return String.format("{\"streams\": [%s]}", tracks);
    }

    @Test
    void execute_withMultipleSubtitles_extractsAllTracks() {
        // Arrange
        String ffprobeJson = generateMockFfprobeOutput("2:eng", "3:jpn"); // Subtitles at stream indices 2 and 3

        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            // Mock static dependencies
            mockedFileUtils.when(() -> FileUtils.getToolPath(any(FileUtils.Tool.class))).thenReturn("tool");
            mockedFileUtils.when(() -> FileUtils.getFilenameWithoutExtension(anyString())).thenReturn(DUMMY_BASE_NAME);

            // Mock processor behavior
            when(mockVideoProcessor.executeFfprobe(any(), eq(mockListener))).thenReturn(CompletableFuture.completedFuture(ffprobeJson));
            when(mockVideoProcessor.executeFfmpeg(any(), eq(mockListener))).thenReturn(CompletableFuture.completedFuture(0)); // Success exit code

            // Act
            subtitleExtractor.execute(DUMMY_INPUT_FILE, mockListener);

            // Assert
            // Verify ffprobe was called once, and ffmpeg was called for each subtitle track (twice)
            verify(mockVideoProcessor, timeout(100)).executeFfprobe(any(), eq(mockListener));
            verify(mockVideoProcessor, timeout(100).times(2)).executeFfmpeg(commandCaptor.capture(), eq(mockListener));

            // Verify the ffmpeg commands are correct
            List<List<String>> ffmpegCommands = commandCaptor.getAllValues();
            // Check first command for English subtitles (stream index 2)
            assertTrue(ffmpegCommands.get(0).containsAll(List.of("-map", "0:2", "/path/to/video-eng.srt")));
            // Check second command for Japanese subtitles (stream index 3)
            assertTrue(ffmpegCommands.get(1).containsAll(List.of("-map", "0:3", "/path/to/video-jpn.srt")));

            // Verify final completion message
            verify(mockListener, timeout(100)).onComplete("Subtitle extraction complete for all tracks.");
            verify(mockListener, never()).onError(anyString());
        }
    }

    @Test
    void execute_withNoSubtitles_completesWithInfoMessage() {
        // Arrange
        String ffprobeJson = generateMockFfprobeOutput(); // No subtitle tracks

        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            mockedFileUtils.when(() -> FileUtils.getToolPath(any())).thenReturn("tool");
            when(mockVideoProcessor.executeFfprobe(any(), any())).thenReturn(CompletableFuture.completedFuture(ffprobeJson));

            // Act
            subtitleExtractor.execute(DUMMY_INPUT_FILE, mockListener);

            // Assert
            verify(mockVideoProcessor, timeout(100)).executeFfprobe(any(), any());
            verify(mockVideoProcessor, never()).executeFfmpeg(any(), any());
            verify(mockListener, timeout(100)).onComplete("No subtitle tracks found in the video.");
            verify(mockListener, never()).onError(anyString());
        }
    }

    @Test
    void execute_withSubtitleMissingLanguageTag_defaultsToUnd() {
        // Arrange
        String ffprobeJson = generateMockFfprobeOutput("2:notags"); // Subtitle at stream index 2, no language tag

        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            mockedFileUtils.when(() -> FileUtils.getToolPath(any())).thenReturn("tool");
            mockedFileUtils.when(() -> FileUtils.getFilenameWithoutExtension(anyString())).thenReturn(DUMMY_BASE_NAME);
            when(mockVideoProcessor.executeFfprobe(any(), any())).thenReturn(CompletableFuture.completedFuture(ffprobeJson));
            when(mockVideoProcessor.executeFfmpeg(any(), any())).thenReturn(CompletableFuture.completedFuture(0));

            // Act
            subtitleExtractor.execute(DUMMY_INPUT_FILE, mockListener);

            // Assert
            verify(mockVideoProcessor, timeout(100)).executeFfmpeg(commandCaptor.capture(), any());
            // Verify output filename uses the default "und" language tag
            assertTrue(commandCaptor.getValue().contains("/path/to/video-und.srt"));
        }
    }

    @Test
    void execute_whenFfprobeFails_reportsError() {
        // Arrange
        RuntimeException ffprobeException = new RuntimeException("ffprobe failed to execute");
        when(mockVideoProcessor.executeFfprobe(any(), any())).thenReturn(CompletableFuture.failedFuture(ffprobeException));

        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            mockedFileUtils.when(() -> FileUtils.getToolPath(any())).thenReturn("tool");

            // Act
            subtitleExtractor.execute(DUMMY_INPUT_FILE, mockListener);

            // Assert
            verify(mockListener, timeout(100)).onError(contains("Error processing video streams:"));
            verify(mockVideoProcessor, never()).executeFfmpeg(any(), any());
            verify(mockListener, never()).onComplete(anyString());
        }
    }

    @Test
    void execute_whenOneFfmpegProcessFails_reportsError() {
        // Arrange
        String ffprobeJson = generateMockFfprobeOutput("2:eng", "3:jpn");

        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            mockedFileUtils.when(() -> FileUtils.getToolPath(any())).thenReturn("tool");
            mockedFileUtils.when(() -> FileUtils.getFilenameWithoutExtension(anyString())).thenReturn(DUMMY_BASE_NAME);
            when(mockVideoProcessor.executeFfprobe(any(), any())).thenReturn(CompletableFuture.completedFuture(ffprobeJson));

            // --- THE FIX IS HERE ---
            // Instead of two separate, potentially conflicting stubs, we use a single,
            // flexible stub with a custom Answer. This is the recommended way to handle
            // conditional return values for the same method.
            when(mockVideoProcessor.executeFfmpeg(any(List.class), any(ProgressListener.class)))
                    .thenAnswer(invocation -> {
                        // Get the first argument, which is the command list
                        List<String> command = invocation.getArgument(0);

                        // Decide what to return based on the command's content
                        if (command != null && command.stream().anyMatch(s -> s.contains("video-jpn.srt"))) {
                            // This is the call we want to fail
                            return CompletableFuture.completedFuture(1); // Non-zero exit code
                        } else {
                            // All other calls (i.e., for the 'eng' track) will succeed
                            return CompletableFuture.completedFuture(0); // Success exit code
                        }
                    });

            // Act
            subtitleExtractor.execute(DUMMY_INPUT_FILE, mockListener);

            // Assert
            // The .exceptionally() block on CompletableFuture.allOf should be triggered by the failed process.
            verify(mockListener, timeout(100)).onError(contains("failed with exit code: 1"));
            verify(mockListener, never()).onComplete(anyString());
        }
    }
}