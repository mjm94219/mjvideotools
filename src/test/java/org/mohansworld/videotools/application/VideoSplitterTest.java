package org.mohansworld.videotools.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@link VideoSplitter} class.
 * These tests verify the correct construction of the ffmpeg command for splitting videos,
 * including the handling of different input path styles.
 */
@ExtendWith(MockitoExtension.class)
class VideoSplitterTest {

    private static final String DUMMY_INPUT_FILE_WITH_PATH = "/path/to/my-awesome-video.mp4";
    private static final String DUMMY_INPUT_FILE_NO_PATH = "my-awesome-video.mp4";
    private static final String DUMMY_BASENAME = "my-awesome-video";
    private static final String DUMMY_EXTENSION = "mp4";

    @Mock
    private FfVideoProcessor mockVideoProcessor;

    @Mock
    private ProgressListener mockListener;

    @Captor
    private ArgumentCaptor<List<String>> commandCaptor;

    @InjectMocks
    private VideoSplitter videoSplitter;

    @Test
    void execute_withFullPath_buildsCorrectCommandWithFullPathOutputPattern() {
        // Arrange
        String segmentTime = "00:10:00";

        // We must mock the static FileUtils methods used by the class under test.
        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            mockedFileUtils.when(() -> FileUtils.getToolPath(FileUtils.Tool.FFMPEG)).thenReturn("ffmpeg");
            mockedFileUtils.when(() -> FileUtils.getFilenameWithoutExtension(DUMMY_INPUT_FILE_NO_PATH)).thenReturn(DUMMY_BASENAME);
            mockedFileUtils.when(() -> FileUtils.getFileExtension(DUMMY_INPUT_FILE_NO_PATH)).thenReturn(DUMMY_EXTENSION);

            // Mock the processor to return a completed future, simulating a successful run.
            when(mockVideoProcessor.executeFfmpeg(any(), any())).thenReturn(CompletableFuture.completedFuture(0));

            // Act
            videoSplitter.execute(DUMMY_INPUT_FILE_WITH_PATH, segmentTime, mockListener);

            // Assert
            // 1. Verify that the processor was called with our listener.
            verify(mockVideoProcessor).executeFfmpeg(commandCaptor.capture(), eq(mockListener));
            List<String> actualCommand = commandCaptor.getValue();

            List<String> expectedCommand = getStrings(segmentTime);
            assertEquals(expectedCommand, actualCommand);
        }
    }

    private static List<String> getStrings(String segmentTime) {
        String expectedOutputPattern = String.format("/path/to/%s-%%04d.%s", DUMMY_BASENAME, DUMMY_EXTENSION);
        List<String> expectedCommand = new ArrayList<>();
        expectedCommand.add("ffmpeg");
        expectedCommand.add("-y");
        expectedCommand.add("-i");
        expectedCommand.add(DUMMY_INPUT_FILE_WITH_PATH);
        expectedCommand.add("-c");
        expectedCommand.add("copy");
        expectedCommand.add("-map");
        expectedCommand.add("0");
        expectedCommand.add("-segment_time");
        expectedCommand.add(segmentTime);
        expectedCommand.add("-f");
        expectedCommand.add("segment");
        expectedCommand.add("-reset_timestamps");
        expectedCommand.add("1");
        expectedCommand.add(expectedOutputPattern);
        return expectedCommand;
    }

    @Test
    void execute_withFileNameOnly_buildsCorrectCommandWithRelativeOutputPattern() {
        // Arrange
        String segmentTime = "600"; // Test with seconds format as well

        // This test case specifically checks the logic for when an input file has no parent path.
        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            mockedFileUtils.when(() -> FileUtils.getToolPath(FileUtils.Tool.FFMPEG)).thenReturn("ffmpeg");
            mockedFileUtils.when(() -> FileUtils.getFilenameWithoutExtension(DUMMY_INPUT_FILE_NO_PATH)).thenReturn(DUMMY_BASENAME);
            mockedFileUtils.when(() -> FileUtils.getFileExtension(DUMMY_INPUT_FILE_NO_PATH)).thenReturn(DUMMY_EXTENSION);
            when(mockVideoProcessor.executeFfmpeg(any(), any())).thenReturn(CompletableFuture.completedFuture(0));

            // Act
            videoSplitter.execute(DUMMY_INPUT_FILE_NO_PATH, segmentTime, mockListener);

            // Assert
            verify(mockVideoProcessor).executeFfmpeg(commandCaptor.capture(), eq(mockListener));
            List<String> actualCommand = commandCaptor.getValue();

            // The output pattern should NOT have a leading path, as the input had none.
            String expectedOutputPattern = String.format("%s-%%04d.%s", DUMMY_BASENAME, DUMMY_EXTENSION);
            List<String> expectedCommand = new ArrayList<>();
            expectedCommand.add("ffmpeg");
            expectedCommand.add("-y");
            expectedCommand.add("-i");
            expectedCommand.add(DUMMY_INPUT_FILE_NO_PATH);
            expectedCommand.add("-c");
            expectedCommand.add("copy");
            expectedCommand.add("-map");
            expectedCommand.add("0");
            expectedCommand.add("-segment_time");
            expectedCommand.add(segmentTime);
            expectedCommand.add("-f");
            expectedCommand.add("segment");
            expectedCommand.add("-reset_timestamps");
            expectedCommand.add("1");
            expectedCommand.add(expectedOutputPattern);
            assertEquals(expectedCommand, actualCommand);
        }
    }
}