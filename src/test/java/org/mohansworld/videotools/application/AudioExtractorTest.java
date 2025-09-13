package org.mohansworld.videotools.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mohansworld.videotools.domain.AudioFormat;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link AudioExtractor} class.
 * These tests use Mockito to mock dependencies like {@link FfVideoProcessor}
 * and {@link ProgressListener} to verify the logic of AudioExtractor in isolation.
 */
@ExtendWith(MockitoExtension.class)
class AudioExtractorTest {

    // Mock the dependency that executes external processes (ffmpeg/ffprobe)
    @Mock
    private FfVideoProcessor mockVideoProcessor;

    // Mock the listener to verify that it receives the correct callbacks
    @Mock
    private ProgressListener mockListener;

    // Captor to capture the command lists passed to ffmpeg for detailed inspection
    @Captor
    private ArgumentCaptor<List<String>> ffmpegCommandCaptor;

    // The class instance we are testing, with mocks automatically injected
    @InjectMocks
    private AudioExtractor audioExtractor;

    // Let's define some constants for our tests to use
    private static final String DUMMY_VIDEO_FILE = "/path/to/my_video.mp4";

    /**
     * Helper method to generate a mock JSON output from ffprobe.
     *
     * @param audioStreamCount The number of audio streams to include in the JSON.
     * @return A JSON string simulating ffprobe output.
     */
    private String generateMockFfprobeOutput(int audioStreamCount) {
        StringBuilder streamsJson = new StringBuilder();
        streamsJson.append("{\"codec_type\": \"video\", \"index\": 0}");

        for (int i = 0; i < audioStreamCount; i++) {
            if (!streamsJson.isEmpty()) {
                streamsJson.append(",");
            }
            streamsJson.append(String.format("{\"codec_type\": \"audio\", \"index\": %d}", i + (1)));
        }

        return String.format("{\"streams\": [%s]}", streamsJson);
    }

    @Test
    void execute_whenVideoHasTwoAudioTracks_shouldExtractBothTracks() {
        // Arrange
        String ffprobeJsonOutput = generateMockFfprobeOutput(2);
        when(mockVideoProcessor.executeFfprobe(any(), eq(mockListener)))
                .thenReturn(CompletableFuture.completedFuture(ffprobeJsonOutput));

        // Act
        audioExtractor.execute(DUMMY_VIDEO_FILE, AudioFormat.MP3, mockListener);

        // Assert
        verify(mockVideoProcessor, times(2)).executeFfmpeg(ffmpegCommandCaptor.capture(), eq(mockListener));
        List<List<String>> allCommands = ffmpegCommandCaptor.getAllValues();
        assertEquals(2, allCommands.size());

        // Check the first command for track 0
        List<String> firstCommand = allCommands.getFirst();
        assertTrue(firstCommand.contains("-map"));
        assertTrue(firstCommand.contains("0:a:0"));
        assertTrue(firstCommand.stream().anyMatch(s -> s.endsWith("my_video-audio-0.mp3")));

        // Check the second command for track 1
        List<String> secondCommand = allCommands.get(1);
        assertTrue(secondCommand.contains("-map"));
        assertTrue(secondCommand.contains("0:a:1"));
        assertTrue(secondCommand.stream().anyMatch(s -> s.endsWith("my_video-audio-1.mp3")));

        verify(mockListener, never()).onError(anyString());
    }

    @Test
    void execute_whenVideoHasNoAudioTracks_shouldCompleteWithInfoMessage() {
        // Arrange
        String ffprobeJsonOutput = generateMockFfprobeOutput(0);
        when(mockVideoProcessor.executeFfprobe(any(), eq(mockListener)))
                .thenReturn(CompletableFuture.completedFuture(ffprobeJsonOutput));

        // Act
        audioExtractor.execute(DUMMY_VIDEO_FILE, AudioFormat.AAC, mockListener);

        // Assert
        verify(mockVideoProcessor, never()).executeFfmpeg(any(), any());
        verify(mockListener).onComplete("No audio tracks found in the video.");
        verify(mockListener, never()).onError(anyString());
    }

    @Test
    void execute_whenFfprobeFails_shouldReportError() {
        // Arrange
        RuntimeException exception = new RuntimeException("ffprobe command failed");
        when(mockVideoProcessor.executeFfprobe(any(), eq(mockListener)))
                .thenReturn(CompletableFuture.failedFuture(exception));

        // Act
        audioExtractor.execute(DUMMY_VIDEO_FILE, AudioFormat.MP3, mockListener);

        // Assert
        verify(mockListener).onError(contains("Error during ffprobe execution:"));
        verify(mockListener).onError(contains("ffprobe command failed"));

        // Verify that no further processing happened
        verify(mockVideoProcessor, never()).executeFfmpeg(any(), any());
        verify(mockListener, never()).onComplete(anyString());
    }

    @Test
    void execute_whenFfprobeReturnsEmptyOutput_shouldReportError() {
        // Arrange
        when(mockVideoProcessor.executeFfprobe(any(), eq(mockListener)))
                .thenReturn(CompletableFuture.completedFuture(""));

        // Act
        // Use WAV as a representative format
        audioExtractor.execute(DUMMY_VIDEO_FILE, AudioFormat.WAV, mockListener);

        // Assert
        verify(mockListener).onError("Failed to get stream information: ffprobe returned empty output.");
        verify(mockVideoProcessor, never()).executeFfmpeg(any(), any());
        verify(mockListener, never()).onComplete(anyString());
    }

    @Test
    void execute_whenFfprobeReturnsInvalidJson_shouldReportError() {
        // Arrange
        String invalidJson = "this is not valid json";
        when(mockVideoProcessor.executeFfprobe(any(), eq(mockListener)))
                .thenReturn(CompletableFuture.completedFuture(invalidJson));

        // Act
        audioExtractor.execute(DUMMY_VIDEO_FILE, AudioFormat.MP3, mockListener);

        // Assert
        verify(mockListener).onError(startsWith("Failed to parse ffprobe JSON output:"));
        verify(mockVideoProcessor, never()).executeFfmpeg(any(), any());
        verify(mockListener, never()).onComplete(anyString());
    }

    @Test
    void execute_whenFfprobeReturnsJsonWithoutStreamsArray_shouldReportError() {
        // Arrange
        String jsonWithoutStreams = "{\"format\": {\"duration\": \"120.5\"}}";
        when(mockVideoProcessor.executeFfprobe(any(), eq(mockListener)))
                .thenReturn(CompletableFuture.completedFuture(jsonWithoutStreams));

        // Act
        audioExtractor.execute(DUMMY_VIDEO_FILE, AudioFormat.MP3, mockListener);

        // Assert
        verify(mockListener).onError(startsWith("Failed to parse ffprobe JSON output:"));
        verify(mockVideoProcessor, never()).executeFfmpeg(any(), any());
        verify(mockListener, never()).onComplete(anyString());
    }

    @Test
    void execute_shouldBuildCorrectFfmpegCommand_withBitrateForMp3() {
        // Arrange
        String ffprobeJsonOutput = generateMockFfprobeOutput(1);
        when(mockVideoProcessor.executeFfprobe(any(), eq(mockListener)))
                .thenReturn(CompletableFuture.completedFuture(ffprobeJsonOutput));

        // Act
        audioExtractor.execute(DUMMY_VIDEO_FILE, AudioFormat.MP3, mockListener);

        // Assert
        verify(mockVideoProcessor).executeFfmpeg(ffmpegCommandCaptor.capture(), eq(mockListener));
        List<String> command = ffmpegCommandCaptor.getValue();

        // Verify that bitrate arguments are present for MP3
        assertTrue(command.contains("-b:a"));
        assertTrue(command.contains("320k"));
    }

    @Test
    void execute_shouldBuildCorrectFfmpegCommand_withoutBitrateForWav() {
        // Arrange
        String ffprobeJsonOutput = generateMockFfprobeOutput(1);
        when(mockVideoProcessor.executeFfprobe(any(), eq(mockListener)))
                .thenReturn(CompletableFuture.completedFuture(ffprobeJsonOutput));

        // Act
        // Use WAV, which should fall into the default case and not have a bitrate argument
        audioExtractor.execute(DUMMY_VIDEO_FILE, AudioFormat.WAV, mockListener);

        // Assert
        verify(mockVideoProcessor).executeFfmpeg(ffmpegCommandCaptor.capture(), eq(mockListener));
        List<String> command = ffmpegCommandCaptor.getValue();

        // Verify that bitrate arguments are NOT present for WAV
        assertFalse(command.contains("-b:a"), "Command should not contain bitrate flag for WAV format");
    }
}