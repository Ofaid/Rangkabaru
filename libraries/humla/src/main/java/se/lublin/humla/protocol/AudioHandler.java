/*
 * Copyright (C) 2014 Andrew Comminos
 * Modif By Ofaid/Ahmad 14-9-2026 — Visualizer Mic dan monitor
 */
package se.lublin.humla.protocol;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.util.Log;

import se.lublin.humla.R;
import se.lublin.humla.audio.AudioInput;
import se.lublin.humla.audio.AudioOutput;
import se.lublin.humla.audio.encoder.CELT11Encoder;
import se.lublin.humla.audio.encoder.CELT7Encoder;
import se.lublin.humla.audio.encoder.IEncoder;
import se.lublin.humla.audio.encoder.OpusEncoder;
import se.lublin.humla.audio.encoder.PreprocessingEncoder;
import se.lublin.humla.audio.encoder.ResamplingEncoder;
import se.lublin.humla.audio.inputmode.IInputMode;
import se.lublin.humla.exception.AudioException;
import se.lublin.humla.exception.AudioInitializationException;
import se.lublin.humla.exception.NativeAudioException;
import se.lublin.humla.model.User;
import se.lublin.humla.net.HumlaConnection;
import se.lublin.humla.net.HumlaUDPMessageType;
import se.lublin.humla.net.PacketBuffer;
import se.lublin.humla.protobuf.Mumble;
import se.lublin.humla.util.HumlaLogger;
import se.lublin.humla.util.HumlaNetworkListener;

public class AudioHandler extends HumlaNetworkListener implements AudioInput.AudioInputListener {
    private static final String TAG = AudioHandler.class.getName();

    public static final int SAMPLE_RATE = 48000;
    public static final int FRAME_SIZE = SAMPLE_RATE/100;
    public static final int MAX_BUFFER_SIZE = 960;

    private final Context mContext;
    private final HumlaLogger mLogger;
    private final AudioManager mAudioManager;
    private final AudioInput mInput;
    private final AudioOutput mOutput;
    private AudioOutput.AudioOutputListener mOutputListener;
    private AudioEncodeListener mEncodeListener;

    private int mSession;
    private HumlaUDPMessageType mCodec;
    private IEncoder mEncoder;
    private int mFrameCounter;

    private final int mAudioStream;
    private final int mAudioSource;
    private int mSampleRate;
    private int mBitrate;
    private int mFramesPerPacket;
    private final IInputMode mInputMode;
    private final float mAmplitudeBoost;

    private boolean mInitialized;
    private boolean mMuted;
    private boolean mBluetoothOn;
    private boolean mHalfDuplex;
    private boolean mPreprocessorEnabled;
    private String mEchoCancellationMethod;
    private boolean mTalking;

    private final Object mEncoderLock;
    private byte mTargetId;

    public AudioHandler(Context context, HumlaLogger logger, int audioStream, int audioSource,
                        int sampleRate, int targetBitrate, int targetFramesPerPacket,
                        IInputMode inputMode, byte targetId, float amplitudeBoost,
                        boolean bluetoothEnabled, boolean halfDuplexEnabled,
                        boolean preprocessorEnabled, String echoCancellationMethod,
                        AudioEncodeListener encodeListener,
                        AudioOutput.AudioOutputListener outputListener) throws AudioInitializationException, NativeAudioException {
        mContext = context;
        mLogger = logger;
        mAudioStream = audioStream;
        mSampleRate = sampleRate;
        mBitrate = targetBitrate;
        mFramesPerPacket = targetFramesPerPacket;
        mInputMode = inputMode;
        mAmplitudeBoost = amplitudeBoost;
        mBluetoothOn = bluetoothEnabled;
        mHalfDuplex = halfDuplexEnabled;
        mPreprocessorEnabled = preprocessorEnabled;
        mEchoCancellationMethod = echoCancellationMethod;
        mEncodeListener = encodeListener;
        mOutputListener = outputListener;
        mTalking = false;
        mTargetId = targetId;

        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mEncoderLock = new Object();

        int actualSource = audioSource;
        if (echoCancellationMethod.equals("system")) {
            AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            actualSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION;
        }
        mAudioSource = actualSource;

        mInput = new AudioInput(this, mAudioSource, mSampleRate, mEchoCancellationMethod);
       // mOutput = new AudioOutput(mOutputListener);
      mOutput = new AudioOutput(mOutputListener, mContext);

    }

    public synchronized void initialize(User self, int maxBandwidth, HumlaUDPMessageType codec) throws AudioException {
        if(mInitialized) return;
        mSession = self.getSession();

        setMaxBandwidth(maxBandwidth);
        setCodec(codec);
        setServerMuted(self.isMuted() || self.isLocalMuted() || self.isSuppressed());
        mOutput.startPlaying(mBluetoothOn ? AudioManager.STREAM_VOICE_CALL : mAudioStream);

        mInitialized = true;
    }

    public void startRecording() throws AudioException {
        synchronized (mInput) {
            if (!mInput.isRecording()) {
                mInput.startRecording();
            } else {
                throw new AudioException("Attempted to start recording while recording!");
            }
        }
    }

    public void stopRecording() throws AudioException {
        synchronized (mInput) {
            if (mInput.isRecording()) {
                mInput.stopRecording();
            } else {
                throw new AudioException("Attempted to stop recording while not recording!");
            }
        }
    }

    private void setServerMuted(boolean muted) throws AudioException {
        mMuted = muted;
    }

    public boolean isInitialized() {
        return mInitialized;
    }

    public boolean isPlaying() {
        synchronized (mOutput) {
            return mOutput.isPlaying();
        }
    }

    public HumlaUDPMessageType getCodec() {
        return mCodec;
    }

    public void recreateEncoder() throws NativeAudioException {
        setCodec(mCodec);
    }

    public void setCodec(HumlaUDPMessageType codec) throws NativeAudioException {
        mCodec = codec;

        if (mEncoder != null) {
            mEncoder.destroy();
            mEncoder = null;
        }

        if (codec == null) {
            Log.w(TAG, "setCodec(null) Input disabled.");
            return;
        }

        IEncoder encoder;
        switch (codec) {
            case UDPVoiceCELTAlpha:
                encoder = new CELT7Encoder(SAMPLE_RATE, AudioHandler.FRAME_SIZE, 1,
                        mFramesPerPacket, mBitrate, MAX_BUFFER_SIZE);
                break;
            case UDPVoiceCELTBeta:
                encoder = new CELT11Encoder(SAMPLE_RATE, 1, mFramesPerPacket);
                break;
            case UDPVoiceOpus:
                encoder = new OpusEncoder(SAMPLE_RATE, 1, FRAME_SIZE, mFramesPerPacket, mBitrate,
                        MAX_BUFFER_SIZE);
                break;
            default:
                Log.w(TAG, "Unsupported codec, input disabled.");
                return;
        }

        if (mPreprocessorEnabled) {
            encoder = new PreprocessingEncoder(encoder, FRAME_SIZE, SAMPLE_RATE);
        }

        if (mInput.getSampleRate() != SAMPLE_RATE) {
            encoder = new ResamplingEncoder(encoder, 1, mInput.getSampleRate(), FRAME_SIZE, SAMPLE_RATE);
        }

        mEncoder = encoder;
    }

    public int getAudioStream() {
        return mAudioStream;
    }

    public int getAudioSource() {
        return mAudioSource;
    }

    public int getSampleRate() {
        return mSampleRate;
    }

    public int getBitrate() {
        return mBitrate;
    }

    private void setMaxBandwidth(int maxBandwidth) throws AudioException {
        if (maxBandwidth == -1) {
            return;
        }
        int bitrate = mBitrate;
        int framesPerPacket = mFramesPerPacket;
        if (HumlaConnection.calculateAudioBandwidth(bitrate, framesPerPacket) > maxBandwidth) {
            if (framesPerPacket <= 4 && maxBandwidth <= 32000) {
                framesPerPacket = 4;
            } else if (framesPerPacket == 1 && maxBandwidth <= 64000) {
                framesPerPacket = 2;
            } else if (framesPerPacket == 2 && maxBandwidth <= 48000) {
                framesPerPacket = 4;
            }
            while (HumlaConnection.calculateAudioBandwidth(bitrate, framesPerPacket)
                    > maxBandwidth && bitrate > 8000) {
                bitrate -= 1000;
            }
        }
        bitrate = Math.max(8000, bitrate);

        if (bitrate != mBitrate ||
                framesPerPacket != mFramesPerPacket) {
            mBitrate = bitrate;
            mFramesPerPacket = framesPerPacket;

            mLogger.logInfo(mContext.getString(R.string.audio_max_bandwidth,
                    maxBandwidth/1000, maxBandwidth/1000, framesPerPacket * 10));
        }
    }

    public int getFramesPerPacket() {
        return mFramesPerPacket;
    }

    public float getAmplitudeBoost() {
        return mAmplitudeBoost;
    }

    public boolean isHalfDuplex() {
        return mHalfDuplex;
    }

    public int getCurrentBandwidth() {
        return HumlaConnection.calculateAudioBandwidth(mBitrate, mFramesPerPacket);
    }

    public synchronized void shutdown() {
        synchronized (mInput) {
            mInput.shutdown();
        }
        synchronized (mOutput) {
            mOutput.stopPlaying();
        }
        synchronized (mEncoderLock) {
            if (mEncoder != null) {
                mEncoder.destroy();
                mEncoder = null;
            }
        }
        mInitialized = false;
        mBluetoothOn = false;

        mEncodeListener.onTalkingStateChanged(false);
    }

    @Override
    public void messageCodecVersion(Mumble.CodecVersion msg) {
        if (!mInitialized)
            return;

        HumlaUDPMessageType codec;
        if (msg.hasOpus() && msg.getOpus()) {
            codec = HumlaUDPMessageType.UDPVoiceOpus;
        } else if (msg.hasBeta() && !msg.getPreferAlpha()) {
            codec = HumlaUDPMessageType.UDPVoiceCELTBeta;
        } else {
            codec = HumlaUDPMessageType.UDPVoiceCELTAlpha;
        }

        if (codec != mCodec) {
            try {
                synchronized (mEncoderLock) {
                    setCodec(codec);
                }
            } catch (NativeAudioException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void messageServerSync(Mumble.ServerSync msg) {
        try {
            setMaxBandwidth(msg.hasMaxBandwidth() ? msg.getMaxBandwidth() : -1);
        } catch (AudioException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void messageUserState(Mumble.UserState msg) {
        if (!mInitialized)
            return;

        if (msg.hasSession() && msg.getSession() == mSession &&
                (msg.hasMute() || msg.hasSelfMute() || msg.hasSuppress())) {
            try {
                setServerMuted(msg.getMute() || msg.getSelfMute() || msg.getSuppress());
            } catch (AudioException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void messageVoiceData(byte[] data, HumlaUDPMessageType messageType) {
        synchronized (mOutput) {
            mOutput.queueVoiceData(data, messageType);
        }
    }

    // ==================================================
    // ✅ TEMPAT UTAMA — BACA DATA SUARA & KIRIM KE VISUAL
    // ==================================================
    @Override
    public void onAudioInputReceived(short[] frame, int frameSize) {
        
        // ✅ === TAMBAHAN: HITUNG & KIRIM KE VISUALIZER ===
        kirimLevelKeVisual(frame, frameSize);
        // === AKHIR TAMBAHAN — DI BAWAH SEMUA TETAP ASLI ===

        boolean talking = mInputMode.shouldTransmit(frame, frameSize);
        talking &= !mMuted;

        if (mTalking ^ talking) {
            mEncodeListener.onTalkingStateChanged(talking);
            if (mHalfDuplex) {
                mAudioManager.setStreamMute(getAudioStream(), talking);
            }

            synchronized (mEncoderLock) {
                if (!talking && mEncoder != null) {
                    try {
                        mEncoder.terminate();
                    } catch (NativeAudioException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if (talking) {
            if (mAmplitudeBoost != 1.0f) {
                for (int i = 0; i < frameSize; i++) {
                    float val = frame[i] * mAmplitudeBoost;
                    if (val > Short.MAX_VALUE) {
                        val = Short.MAX_VALUE;
                    } else if (val < Short.MIN_VALUE) {
                        val = Short.MIN_VALUE;
                    }
                    frame[i] = (short) val;
                }
            }

            synchronized (mEncoderLock) {
                if (mEncoder != null) {
                    try {
                        mEncoder.encode(frame, frameSize);
                        mFrameCounter++;
                    } catch (NativeAudioException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        synchronized (mEncoderLock) {
            if (mEncoder != null && mEncoder.isReady()) {
                sendEncodedAudio();
            }
        }

        mTalking = talking;
        if (!talking) {
            mInputMode.waitForInput();
        }
    }

    // ==================================================
    // ✅ FUNGSI BARU — HITUNG KEKUATAN SUARA & KIRIM
    // ==================================================
    private void kirimLevelKeVisual(short[] frame, int frameSize) {
        if (frame == null || frameSize <= 0 || mContext == null) return;

        // Rumus persis seperti contoh — RMS level dari data PCM
        double sum = 0;
        for (int i = 0; i < frameSize; i++) {
            sum += frame[i] * frame[i];
        }
        double rms = Math.sqrt(sum / frameSize);
        float level = (float) Math.min(rms / 32768.0f, 1.0f);

        // Kirim lewat Broadcast — tidak sentuh jalur suara!
        Intent kirim = new Intent("ofaid.ahmad.ptt.LEVEL_SUARA");
        kirim.putExtra("level", level);
        mContext.sendBroadcast(kirim);
    }

    public void setVoiceTargetId(byte id) {
        mTargetId = id;
    }

    public void clearVoiceTarget() {
        mTargetId = 0;
    }

    private void sendEncodedAudio() {
        int frames = mEncoder.getBufferedFrames();

        int flags = 0;
        flags |= mCodec.ordinal() << 5;
        flags |= mTargetId & 0x1F;

        final byte[] packetBuffer = new byte[1024];
        packetBuffer[0] = (byte) (flags & 0xFF);

        PacketBuffer ds = new PacketBuffer(packetBuffer, 1024);
        ds.skip(1);
        ds.writeLong(mFrameCounter - frames);
        mEncoder.getEncodedData(ds);
        int length = ds.size();
        ds.rewind();

        byte[] packet = ds.dataBlock(length);
        mEncodeListener.onAudioEncoded(packet, length);
    }

    public interface AudioEncodeListener {
        void onAudioEncoded(byte[] data, int length);
        void onTalkingStateChanged(boolean talking);
    }

    public static class Builder {
        private Context mContext;
        private HumlaLogger mLogger;
        private int mAudioStream;
        private int mAudioSource;
        private int mTargetBitrate;
        private int mTargetFramesPerPacket;
        private int mInputSampleRate;
        private float mAmplitudeBoost;
        private boolean mBluetoothEnabled;
        private boolean mHalfDuplexEnabled;
        private boolean mPreprocessorEnabled;
        private String mEchoCancellationMethod;
        private IInputMode mInputMode;
        private AudioEncodeListener mEncodeListener;
        private AudioOutput.AudioOutputListener mTalkingListener;

        public Builder setContext(Context context) {
            mContext = context;
            return this;
        }

        public Builder setLogger(HumlaLogger logger) {
            mLogger = logger;
            return this;
        }

        public Builder setAudioStream(int audioStream) {
            mAudioStream = audioStream;
            return this;
        }

        public Builder setAudioSource(int audioSource) {
            mAudioSource = audioSource;
            return this;
        }

        public Builder setTargetBitrate(int targetBitrate) {
            mTargetBitrate = targetBitrate;
            return this;
        }

        public Builder setTargetFramesPerPacket(int targetFramesPerPacket) {
            mTargetFramesPerPacket = targetFramesPerPacket;
            return this;
        }

        public Builder setInputSampleRate(int inputSampleRate) {
            mInputSampleRate = inputSampleRate;
            return this;
        }

        public Builder setAmplitudeBoost(float amplitudeBoost) {
            mAmplitudeBoost = amplitudeBoost;
            return this;
        }

        public Builder setBluetoothEnabled(boolean bluetoothEnabled) {
            mBluetoothEnabled = bluetoothEnabled;
            return this;
        }

        public Builder setHalfDuplexEnabled(boolean halfDuplexEnabled) {
            mHalfDuplexEnabled = halfDuplexEnabled;
            return this;
        }

        public Builder setPreprocessorEnabled(boolean preprocessorEnabled) {
            mPreprocessorEnabled = preprocessorEnabled;
            return this;
        }

        public Builder setEchoCancellationMethod(String echoCancellationMethod) {
            mEchoCancellationMethod = echoCancellationMethod;
            return this;
        }

        public Builder setEncodeListener(AudioEncodeListener encodeListener) {
            mEncodeListener = encodeListener;
            return this;
        }

        public Builder setTalkingListener(AudioOutput.AudioOutputListener talkingListener) {
            mTalkingListener = talkingListener;
            return this;
        }

        public Builder setInputMode(IInputMode inputMode) {
            mInputMode = inputMode;
            return this;
        }

        public AudioHandler initialize(User self, int maxBandwidth, HumlaUDPMessageType codec, byte targetId) throws AudioException {
            AudioHandler handler = new AudioHandler(mContext, mLogger, mAudioStream, mAudioSource,
                    mInputSampleRate, mTargetBitrate, mTargetFramesPerPacket, mInputMode, targetId,
                    mAmplitudeBoost, mBluetoothEnabled, mHalfDuplexEnabled,
                    mPreprocessorEnabled, mEchoCancellationMethod, mEncodeListener, mTalkingListener);
            handler.initialize(self, maxBandwidth, codec);
            return handler;
        }
    }
}
