package org.mohansworld.videotools.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mohansworld.videotools.domain.MkvPropertyInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@link MkvPropertyEditor} class.
 * These tests verify that the correct mkvmerge/mkvpropedit commands are constructed
 * and that the class correctly handles success and failure scenarios from its processor dependency.
 */
@ExtendWith(MockitoExtension.class)
class MkvPropertyEditorTest {

    private static final String DUMMY_FILE_PATH = "/path/to/video.mkv";

    @Mock
    private MkvVideoProcessor mockVideoProcessor;

    @Mock
    private ProgressListener mockListener;

    @Captor
    private ArgumentCaptor<List<String>> commandCaptor;

    @InjectMocks
    private MkvPropertyEditor mkvPropertyEditor;

    /**
     * A realistic JSON sample output from 'mkvmerge -J'.
     * This is used to test the parsing logic within MkvPropertyInfo via the getProperties method.
     */
    private static final String REALISTIC_MKV_JSON = """
            {
              "container": {
                "properties": { "title": "My Test Movie" }
              },
              "tracks": [
                {
                  "id": 0, "type": "video", "codec": "V_MPEGH/ISO/HEVC",
                  "properties": { "track_name": "Main Video", "default_track": true, "enabled_track": true, "language": "und" }
                },
                {
                  "id": 1, "type": "audio", "codec": "A_AC3",
                  "properties": { "track_name": "English Audio", "default_track": true, "enabled_track": true, "language": "eng" }
                },
                {
                  "id": 2, "type": "subtitles", "codec": "S_HDMV/PGS",
                  "properties": { "track_name": "Forced Subtitles", "default_track": false, "enabled_track": true, "language": "eng" }
                }
              ]
            }
            """;


    // --- Tests for getProperties() ---

    @Test
    void getProperties_whenSuccessful_shouldReturnCorrectlyParsedMkvPropertyInfo() throws ExecutionException, InterruptedException {
        // Arrange
        // Use the realistic JSON sample to test the full parsing path.
        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            mockedFileUtils.when(() -> FileUtils.getToolPath(FileUtils.Tool.MKVMERGE)).thenReturn("mkvmerge");

            // Mock the processor to return a completed future with our JSON
            when(mockVideoProcessor.executeMkv(any(), eq(mockListener)))
                    .thenReturn(CompletableFuture.completedFuture(REALISTIC_MKV_JSON));

            // Act
            CompletableFuture<MkvPropertyInfo> future = mkvPropertyEditor.getProperties(DUMMY_FILE_PATH, mockListener);
            MkvPropertyInfo result = future.get(); // Wait for the future to complete

            // Assert
            // 1. Verify the correct command was sent to the processor
            verify(mockVideoProcessor).executeMkv(commandCaptor.capture(), eq(mockListener));
            List<String> capturedCommand = commandCaptor.getValue();
            assertEquals(List.of("mkvmerge", "-J", DUMMY_FILE_PATH), capturedCommand);

            // 2. Verify the parsed MkvPropertyInfo object has the correct data
            assertNotNull(result);
            assertEquals("My Test Movie", result.getTitle());
            assertEquals(3, result.getTracks().size());

            // 3. Verify individual tracks were parsed correctly
            MkvPropertyInfo.Track videoTrack = result.getTracks().getFirst();
            assertEquals("v1", videoTrack.getSelector());
            assertEquals(MkvPropertyInfo.TrackType.VIDEO, videoTrack.getType());
            assertEquals("Main Video", videoTrack.getTrackName());
            assertTrue(videoTrack.isDefaultTrack());

            MkvPropertyInfo.Track audioTrack = result.getTracks().get(1);
            assertEquals("a1", audioTrack.getSelector());
            assertEquals(MkvPropertyInfo.TrackType.AUDIO, audioTrack.getType());
            assertEquals("eng", audioTrack.getLanguage());
            assertTrue(audioTrack.isDefaultTrack());

            MkvPropertyInfo.Track subtitleTrack = result.getTracks().get(2);
            assertEquals("s1", subtitleTrack.getSelector());
            assertEquals(MkvPropertyInfo.TrackType.SUBTITLES, subtitleTrack.getType());
            assertEquals("Forced Subtitles", subtitleTrack.getTrackName());
            assertFalse(subtitleTrack.isDefaultTrack());
        }
    }

    @Test
    void getProperties_whenJsonIsInvalid_shouldReturnFailedFuture() {
        // Arrange
        String invalidJson = "{ not json }";
        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            mockedFileUtils.when(() -> FileUtils.getToolPath(FileUtils.Tool.MKVMERGE)).thenReturn("mkvmerge");
            when(mockVideoProcessor.executeMkv(any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(invalidJson));

            // Act
            CompletableFuture<MkvPropertyInfo> future = mkvPropertyEditor.getProperties(DUMMY_FILE_PATH, mockListener);

            // Assert
            // MkvPropertyInfo.fromJson throws IllegalArgumentException, which gets wrapped in CompletionException
            CompletionException thrown = assertThrows(CompletionException.class, future::join);
            assertInstanceOf(IllegalArgumentException.class, thrown.getCause());
            assertTrue(thrown.getCause().getMessage().contains("Failed to parse MKV properties from JSON"));
        }
    }

    @Test
    void getProperties_whenProcessorFails_shouldReturnFailedFuture() {
        // Arrange
        RuntimeException testException = new RuntimeException("mkvmerge failed");
        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            mockedFileUtils.when(() -> FileUtils.getToolPath(FileUtils.Tool.MKVMERGE)).thenReturn("mkvmerge");
            when(mockVideoProcessor.executeMkv(any(), any()))
                    .thenReturn(CompletableFuture.failedFuture(testException));

            // Act
            CompletableFuture<MkvPropertyInfo> future = mkvPropertyEditor.getProperties(DUMMY_FILE_PATH, mockListener);

            // Assert
            CompletionException thrown = assertThrows(CompletionException.class, future::join);
            assertEquals(testException, thrown.getCause());
        }
    }

    // --- Tests for updateProperties() ---
    // These tests do not need to change as they only verify command construction logic,
    // which is independent of the MkvPropertyInfo data structure.

    @Test
    void updateProperties_withTitleAndMultipleTracks_shouldBuildCorrectCommand() {
        // Arrange
        String newTitle = "My Awesome Movie";
        List<MkvPropertyEditor.TrackUpdateInfo> updates = List.of(
                new MkvPropertyEditor.TrackUpdateInfo("v1", "Main Video", "1", "1"),
                new MkvPropertyEditor.TrackUpdateInfo("a1", "English Audio", "1", "0")
        );

        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            mockedFileUtils.when(() -> FileUtils.getToolPath(FileUtils.Tool.MKVPROPEDIT)).thenReturn("mkvpropedit");
            when(mockVideoProcessor.executeMkv(any(), any())).thenReturn(CompletableFuture.completedFuture(""));

            // Act
            mkvPropertyEditor.updateProperties(DUMMY_FILE_PATH, newTitle, updates, mockListener).join();

            // Assert
            verify(mockVideoProcessor).executeMkv(commandCaptor.capture(), eq(mockListener));
            List<String> command = commandCaptor.getValue();
            List<String> expectedCommand = List.of(
                    "mkvpropedit", DUMMY_FILE_PATH,
                    "--edit", "info", "--set", "title=" + newTitle,
                    "--edit", "track:v1", "--set", "name=Main Video", "--set", "flag-enabled=1", "--set", "flag-default=1",
                    "--edit", "track:a1", "--set", "name=English Audio", "--set", "flag-enabled=1", "--set", "flag-default=0"
            );
            assertEquals(expectedCommand, command);
        }
    }

    @Test
    void updateProperties_withTitleOnly_shouldBuildCorrectCommand() {
        // Arrange
        String newTitle = "Title Only Edit";
        List<MkvPropertyEditor.TrackUpdateInfo> emptyUpdates = List.of();

        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            mockedFileUtils.when(() -> FileUtils.getToolPath(FileUtils.Tool.MKVPROPEDIT)).thenReturn("mkvpropedit");
            when(mockVideoProcessor.executeMkv(any(), any())).thenReturn(CompletableFuture.completedFuture(""));

            // Act
            mkvPropertyEditor.updateProperties(DUMMY_FILE_PATH, newTitle, emptyUpdates, mockListener).join();

            // Assert
            verify(mockVideoProcessor).executeMkv(commandCaptor.capture(), eq(mockListener));
            List<String> command = commandCaptor.getValue();
            List<String> expectedCommand = List.of(
                    "mkvpropedit", DUMMY_FILE_PATH,
                    "--edit", "info", "--set", "title=" + newTitle
            );
            assertEquals(expectedCommand, command);
        }
    }
}