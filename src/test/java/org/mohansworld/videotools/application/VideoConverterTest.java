package org.mohansworld.videotools.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mohansworld.videotools.domain.VideoFormat;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@link VideoConverter} class.
 * These tests verify that the correct ffmpeg command is constructed for different
 * target video formats, especially focusing on the subtitle handling logic.
 */
@ExtendWith(MockitoExtension.class)
class VideoConverterTest {

    private static final String DUMMY_INPUT_FILE = "/path/to/video.mkv";
    private static final String DUMMY_INPUT_BASENAME = "video";

    @Mock
    private FfVideoProcessor mockVideoProcessor;

    @Mock
    private ProgressListener mockListener;

    @Captor
    private ArgumentCaptor<List<String>> commandCaptor;

    @InjectMocks
    private VideoConverter videoConverter;

    @Test
    void execute_whenConvertingToMp4_shouldBuildCorrectCommandWithMovTextSubtitles() {
        // Arrange
        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            // Mock static dependencies to control their return values
            mockedFileUtils.when(() -> FileUtils.getToolPath(FileUtils.Tool.FFMPEG)).thenReturn("ffmpeg");
            mockedFileUtils.when(() -> FileUtils.getFilenameWithoutExtension("video.mkv")).thenReturn(DUMMY_INPUT_BASENAME);

            // Mock the processor to simulate a successful execution
            when(mockVideoProcessor.executeFfmpeg(any(), any())).thenReturn(CompletableFuture.completedFuture(0));

            // Act
            videoConverter.execute(DUMMY_INPUT_FILE, VideoFormat.MP4, mockListener);

            // Assert
            // 1. Verify that the processor's execute method was called
            verify(mockVideoProcessor).executeFfmpeg(commandCaptor.capture(), eq(mockListener));
            List<String> actualCommand = commandCaptor.getValue();

            // 2. Construct the expected command and verify it matches
            List<String> expectedCommand = List.of(
                    "ffmpeg",
                    "-y",
                    "-i", DUMMY_INPUT_FILE,
                    "-map", "0",
                    "-c:v", "copy",
                    "-c:a", "copy",
                    "-c:s", "mov_text", // Specific command for MP4 format
                    "/path/to/video-converted.mp4"
            );
            assertEquals(expectedCommand, actualCommand);
        }
    }

    @Test
    void execute_whenConvertingToMkv_shouldBuildCorrectCommandWithCopySubtitles() {
        // Arrange
        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            mockedFileUtils.when(() -> FileUtils.getToolPath(FileUtils.Tool.FFMPEG)).thenReturn("ffmpeg");
            mockedFileUtils.when(() -> FileUtils.getFilenameWithoutExtension("video.mkv")).thenReturn(DUMMY_INPUT_BASENAME);
            when(mockVideoProcessor.executeFfmpeg(any(), any())).thenReturn(CompletableFuture.completedFuture(0));

            // Act
            videoConverter.execute(DUMMY_INPUT_FILE, VideoFormat.MKV, mockListener);

            // Assert
            verify(mockVideoProcessor).executeFfmpeg(commandCaptor.capture(), eq(mockListener));
            List<String> actualCommand = commandCaptor.getValue();

            List<String> expectedCommand = List.of(
                    "ffmpeg",
                    "-y",
                    "-i", DUMMY_INPUT_FILE,
                    "-map", "0",
                    "-c:v", "copy",
                    "-c:a", "copy",
                    "-c:s", "copy", // Specific command for MKV format
                    "/path/to/video-converted.mkv"
            );
            assertEquals(expectedCommand, actualCommand);
        }
    }

    @Test
    void execute_whenConvertingToMov_shouldBuildCorrectCommandWithMovTextSubtitles() {
        // Arrange
        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            mockedFileUtils.when(() -> FileUtils.getToolPath(FileUtils.Tool.FFMPEG)).thenReturn("ffmpeg");
            mockedFileUtils.when(() -> FileUtils.getFilenameWithoutExtension("video.mkv")).thenReturn(DUMMY_INPUT_BASENAME);
            when(mockVideoProcessor.executeFfmpeg(any(), any())).thenReturn(CompletableFuture.completedFuture(0));

            // Act
            videoConverter.execute(DUMMY_INPUT_FILE, VideoFormat.MOV, mockListener);

            // Assert
            verify(mockVideoProcessor).executeFfmpeg(commandCaptor.capture(), eq(mockListener));
            List<String> actualCommand = commandCaptor.getValue();

            List<String> expectedCommand = List.of(
                    "ffmpeg",
                    "-y",
                    "-i", DUMMY_INPUT_FILE,
                    "-map", "0",
                    "-c:v", "copy",
                    "-c:a", "copy",
                    "-c:s", "mov_text", // Specific command for MOV format (same as MP4)
                    "/path/to/video-converted.mov"
            );
            assertEquals(expectedCommand, actualCommand);
        }
    }
}