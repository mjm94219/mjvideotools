package org.mohansworld.videotools.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@link MkvTrackRemover} class.
 * These tests focus on verifying that the correct mkvmerge command is constructed
 * based on the provided {@link MkvTrackRemover.TrackRemovalOptions}.
 */
@ExtendWith(MockitoExtension.class)
class MkvTrackRemoverTest {

    private static final String DUMMY_INPUT_FILE = "/path/to/input.mkv";
    private static final String DUMMY_OUTPUT_FILE = "/path/to/output.mkv";

    @Mock
    private MkvVideoProcessor mockVideoProcessor;

    @Mock
    private ProgressListener mockListener;

    @Captor
    private ArgumentCaptor<List<String>> commandCaptor;

    @InjectMocks
    private MkvTrackRemover mkvTrackRemover;

    @Test
    void removeTracks_whenKeepingSomeOfEachTrackType_shouldBuildCorrectCommand() {
        // Arrange
        var options = new MkvTrackRemover.TrackRemovalOptions(
                DUMMY_INPUT_FILE,
                DUMMY_OUTPUT_FILE,
                List.of(1, 3),  // Video tracks to keep
                List.of(2),     // Audio track to keep
                List.of(4, 5)   // Subtitle tracks to keep
        );

        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            mockedFileUtils.when(() -> FileUtils.getToolPath(FileUtils.Tool.MKVMERGE)).thenReturn("mkvmerge");
            when(mockVideoProcessor.executeMkv(any(), any())).thenReturn(CompletableFuture.completedFuture(""));

            // Act
            mkvTrackRemover.removeTracks(options, mockListener).join();

            // Assert
            verify(mockVideoProcessor).executeMkv(commandCaptor.capture(), eq(mockListener));
            List<String> actualCommand = commandCaptor.getValue();
            List<String> expectedCommand = List.of(
                    "mkvmerge",
                    "-o", DUMMY_OUTPUT_FILE,
                    "-d", "1,3",
                    "-a", "2",
                    "-s", "4,5",
                    "--no-attachments",
                    DUMMY_INPUT_FILE
            );
            assertEquals(expectedCommand, actualCommand);
        }
    }

    @Test
    void removeTracks_whenRemovingAllVideoTracks_shouldUseNoVideoFlag() {
        // Arrange
        var options = new MkvTrackRemover.TrackRemovalOptions(
                DUMMY_INPUT_FILE,
                DUMMY_OUTPUT_FILE,
                List.of(),      // Remove all video tracks
                List.of(1),     // Keep audio track 1
                List.of(2)      // Keep subtitle track 2
        );

        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            mockedFileUtils.when(() -> FileUtils.getToolPath(FileUtils.Tool.MKVMERGE)).thenReturn("mkvmerge");
            when(mockVideoProcessor.executeMkv(any(), any())).thenReturn(CompletableFuture.completedFuture(""));

            // Act
            mkvTrackRemover.removeTracks(options, mockListener).join();

            // Assert
            verify(mockVideoProcessor).executeMkv(commandCaptor.capture(), eq(mockListener));
            List<String> actualCommand = commandCaptor.getValue();
            List<String> expectedCommand = List.of(
                    "mkvmerge",
                    "-o", DUMMY_OUTPUT_FILE,
                    "--no-video",
                    "-a", "1",
                    "-s", "2",
                    "--no-attachments",
                    DUMMY_INPUT_FILE
            );
            assertEquals(expectedCommand, actualCommand);
        }
    }

    @Test
    void removeTracks_whenRemovingAllAudioAndSubtitles_shouldUseCorrectFlags() {
        // Arrange
        var options = new MkvTrackRemover.TrackRemovalOptions(
                DUMMY_INPUT_FILE,
                DUMMY_OUTPUT_FILE,
                List.of(1),   // Keep video
                List.of(),    // Remove all audio
                List.of()     // Remove all subtitles
        );

        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            mockedFileUtils.when(() -> FileUtils.getToolPath(FileUtils.Tool.MKVMERGE)).thenReturn("mkvmerge");
            when(mockVideoProcessor.executeMkv(any(), any())).thenReturn(CompletableFuture.completedFuture(""));

            // Act
            mkvTrackRemover.removeTracks(options, mockListener).join();

            // Assert
            verify(mockVideoProcessor).executeMkv(commandCaptor.capture(), eq(mockListener));
            List<String> actualCommand = commandCaptor.getValue();
            List<String> expectedCommand = List.of(
                    "mkvmerge",
                    "-o", DUMMY_OUTPUT_FILE,
                    "-d", "1",
                    "--no-audio",
                    "--no-subtitles",
                    "--no-attachments",
                    DUMMY_INPUT_FILE
            );
            assertEquals(expectedCommand, actualCommand);
        }
    }

    @Test
    void removeTracks_whenKeepListsAreNull_shouldBeTreatedAsEmptyAndUseRemoveAllFlags() {
        // Arrange
        // This tests the null-safety of the TrackRemovalOptions record's compact constructor
        var options = new MkvTrackRemover.TrackRemovalOptions(
                DUMMY_INPUT_FILE,
                DUMMY_OUTPUT_FILE,
                null,  // should become empty list
                null,  // should become empty list
                null   // should become empty list
        );

        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            mockedFileUtils.when(() -> FileUtils.getToolPath(FileUtils.Tool.MKVMERGE)).thenReturn("mkvmerge");
            when(mockVideoProcessor.executeMkv(any(), any())).thenReturn(CompletableFuture.completedFuture(""));

            // Act
            mkvTrackRemover.removeTracks(options, mockListener).join();

            // Assert
            verify(mockVideoProcessor).executeMkv(commandCaptor.capture(), eq(mockListener));
            List<String> actualCommand = commandCaptor.getValue();
            List<String> expectedCommand = List.of(
                    "mkvmerge",
                    "-o", DUMMY_OUTPUT_FILE,
                    "--no-video",
                    "--no-audio",
                    "--no-subtitles",
                    "--no-attachments",
                    DUMMY_INPUT_FILE
            );
            assertEquals(expectedCommand, actualCommand);
        }
    }

    @Test
    void removeTracks_whenProcessorFails_shouldReturnFailedFuture() {
        // Arrange
        var options = new MkvTrackRemover.TrackRemovalOptions(DUMMY_INPUT_FILE, DUMMY_OUTPUT_FILE, List.of(1), List.of(1), List.of(1));
        RuntimeException testException = new RuntimeException("mkvmerge failed");

        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            mockedFileUtils.when(() -> FileUtils.getToolPath(FileUtils.Tool.MKVMERGE)).thenReturn("mkvmerge");
            when(mockVideoProcessor.executeMkv(any(), any())).thenReturn(CompletableFuture.failedFuture(testException));

            // Act
            CompletableFuture<Void> future = mkvTrackRemover.removeTracks(options, mockListener);

            // Assert
            CompletionException thrown = assertThrows(CompletionException.class, future::join);
            assertEquals(testException, thrown.getCause());
        }
    }
}