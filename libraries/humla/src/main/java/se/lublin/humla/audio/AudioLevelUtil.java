package se.lublin.humla.audio;

public final class AudioLevelUtil {
    private static final double MAX_DB = -6.0d;
    private static final double MIN_DB = -50.0d;

    private AudioLevelUtil() {
        // Utility class, prevent instantiation
    }

    public static float computeLevel(short[] frame, int frameSize) {
        if (frame == null || frameSize <= 0) {
            return 0.0f;
        }
        
        long sumSquares = 0;
        for (int i = 0; i < frameSize; i++) {
            sumSquares += frame[i] * frame[i];
        }
        
        double rms = Math.sqrt(sumSquares / frameSize);
        if (rms < 1.0d) {
            return 0.0f;
        }
        
        // Convert RMS to dBFS
        double dbfs = Math.log10(rms / 32768.0d) * 20.0d;
        
        // Map dB range (-50 to -6) to 0.0 - 1.0
        float level = (float) ((dbfs - MIN_DB) / (MAX_DB - MIN_DB));
        
        // Clamp values
        if (level < 0.0f) {
            return 0.0f;
        }
        if (level > 1.0f) {
            return 1.0f;
        }
        
        return level;
    }
}