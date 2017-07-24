/*
 * Copyright (C) 2013 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.nick.library.cast;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;

import dev.nick.library.AudioSource;

public class RecordingDevice extends EncoderDevice {

    private static final String LOGTAG = "RecordingDevice";
    private File mFile;
    private boolean mRecordAudio;
    private int mAudioSource;

    public RecordingDevice(Context context, int width, int height, boolean recordAudio, int audioSource, int orientation, String path) {
        super(context, width, height, orientation);
        mRecordAudio = recordAudio;
        this.mFile = new File(path);
        this.mAudioSource = audioSource;
    }

    /**
     * @return the mFile of the screen cast file.
     */
    public String getRecordingFilePath() {
        return mFile.getAbsolutePath();
    }

    @Override
    protected EncoderRunnable onSurfaceCreated(MediaCodec venc) {
        return new Recorder(venc);
    }

    // thread to mux the encoded audio into the final mp4 file
    private class AudioMuxer implements Runnable {
        AudioRecorder audio;
        MediaMuxer muxer;
        int track;
        // the video encoder waits for the audio muxer to get ready with
        // this semaphore
        Semaphore muxWaiter;

        AudioMuxer(AudioRecorder audio, MediaMuxer muxer, Semaphore muxWaiter) {
            this.audio = audio;
            this.muxer = muxer;
            this.muxWaiter = muxWaiter;
        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        @Override
        public void run() {
            try {
                if (audio.record.getState() != AudioRecord.STATE_INITIALIZED) {
                    muxer.start();
                    return;
                }
                encode();
            } catch (Exception e) {
                Log.e(LOGTAG, "Audio Muxer error", e);
            } finally {
                Log.i(LOGTAG, "AudioMuxer done");
                muxWaiter.release();
            }
        }

        void encode() throws Exception {
            ByteBuffer[] outs = audio.codec.getOutputBuffers();
            boolean doneCoding = false;
            // used to rewrite the presentation timestamps into something 0 based
            long start = System.nanoTime();
            while (!doneCoding) {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                int bufIndex = audio.codec.dequeueOutputBuffer(info, -1);
                if (bufIndex >= 0) {
                    ByteBuffer b = outs[bufIndex];

                    info.presentationTimeUs = (System.nanoTime() - start) / 1000L;
                    muxer.writeSampleData(track, b, info);

                    audio.codec.releaseOutputBuffer(bufIndex, false);
                    doneCoding = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                } else if (bufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outs = audio.codec.getOutputBuffers();
                } else if (bufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = audio.codec.getOutputFormat();
                    track = muxer.addTrack(newFormat);
                    muxer.start();
                    muxWaiter.release();
                }
            }
        }
    }

    // Start up an AudioRecord thread to record the mic, and feed
    // the data to an encoder.
    private class AudioRecorder implements Runnable {
        Recorder recorder;
        AudioRecord record;
        MediaCodec codec;
        MediaFormat format;

        AudioRecorder(Recorder recorder) {
            try {
                codec = MediaCodec.createEncoderByType("audio/mp4a-latm");
            } catch (IOException e) {
                Log.wtf(LOGTAG, "Can'data create encoder!", e);
            }
            format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
            format.setInteger(MediaFormat.KEY_BIT_RATE, 64 * 1024);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectHE);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();

            this.recorder = recorder;

            int bufferSize = 1024 * 30;
            int minBufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize < minBufferSize)
                bufferSize = ((minBufferSize / 1024) + 1) * 1024 * 2;
            Log.i(LOGTAG, "AudioRecorder init");
            int audioSource =
                    mAudioSource == AudioSource.MIC
                            ? MediaRecorder.AudioSource.MIC
                            : MediaRecorder.AudioSource.REMOTE_SUBMIX;
            Log.i(LOGTAG, "Using audio source:" + audioSource);
            record = new AudioRecord(audioSource, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        }

        @Override
        public void run() {
            try {
                Log.i(LOGTAG, "AudioRecorder start");
                record.startRecording();
                encode();
            } catch (Exception e) {
                Log.e(LOGTAG, "AudioRecorder error", e);
            }
            Log.i(LOGTAG, "AudioRecorder done");
            try {
                record.stop();
            } catch (Exception e) {
                Log.e(LOGTAG, "AudioRecorder error", e);
            }
            try {
                record.release();
            } catch (Exception e) {
                Log.e(LOGTAG, "AudioRecorder error", e);
            }
        }

        void encode() throws Exception {
            ByteBuffer[] inputs = codec.getInputBuffers();
            while (!recorder.doneCoding) {
                int bufIndex = codec.dequeueInputBuffer(1024);
                if (bufIndex < 0)
                    continue;
                ByteBuffer b = inputs[bufIndex];
                b.clear();
                int size = record.read(b, b.capacity());
                size = size < 0 ? 0 : size;
                b.clear();
                codec.queueInputBuffer(bufIndex, 0, size, System.nanoTime() / 1000L, 0);
            }
            int bufIndex = codec.dequeueInputBuffer(-1);
            codec.queueInputBuffer(bufIndex, 0, 0, System.nanoTime() / 1000L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        }
    }

    private class Recorder extends EncoderRunnable {
        boolean doneCoding = false;

        Recorder(MediaCodec venc) {
            super(venc);
        }

        @Override
        protected void cleanup() {
            super.cleanup();
            doneCoding = true;
        }

        @Override
        public void encode() throws Exception {
            File recordingDir = mFile.getParentFile();
            recordingDir.mkdirs();
            if (!(recordingDir.exists() && recordingDir.canWrite())) {
                throw new SecurityException("Cannot write to " + recordingDir);
            }
            MediaMuxer muxer = new MediaMuxer(mFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            boolean muxerStarted = false;
            int trackIndex = -1;
            Thread audioThread = null;
            AudioMuxer audioMuxer;
            AudioRecorder audio;

            ByteBuffer[] encouts = venc.getOutputBuffers();
            long start = System.nanoTime();
            while (!doneCoding) {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                int bufIndex = venc.dequeueOutputBuffer(info, -1);
                if (bufIndex >= 0) {
                    Log.i(LOGTAG, "Dequeued buffer " + info.presentationTimeUs);

                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // The codec config data was pulled out and fed to the muxer when we got
                        // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                        Log.d(LOGTAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                        info.size = 0;
                    }

                    if (!muxerStarted) {
                        throw new RuntimeException("muxer hasn'data started");
                    }

                    ByteBuffer b = encouts[bufIndex];

                    info.presentationTimeUs = (System.nanoTime() - start) / 1000L;
                    muxer.writeSampleData(trackIndex, b, info);

                    b.clear();
                    venc.releaseOutputBuffer(bufIndex, false);

                    doneCoding = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                } else if (bufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    encouts = venc.getOutputBuffers();
                } else if (bufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // should happen before receiving buffers, and should only happen once
                    if (muxerStarted) {
                        throw new RuntimeException("format changed twice");
                    }
                    MediaFormat newFormat = venc.getOutputFormat();
                    Log.d(LOGTAG, "encoder output format changed: " + newFormat);

                    // now that we have the Magic Goodies, start the muxer
                    trackIndex = muxer.addTrack(newFormat);
                    if (mRecordAudio) {
                        audio = new AudioRecorder(this);
                        Semaphore semaphore = new Semaphore(0);
                        audioMuxer = new AudioMuxer(audio, muxer, semaphore);
                        muxerStarted = true;
                        new Thread(audio, "AudioRecorder").start();
                        audioThread = new Thread(audioMuxer, "AudioMuxer");
                        audioThread.start();

                        semaphore.acquire();
                    } else {
                        muxer.start();
                        muxerStarted = true;
                    }
                    Log.i(LOGTAG, "Muxing");
                }
            }
            doneCoding = true;
            Log.i(LOGTAG, "Done recording");
            if (audioThread != null)
                audioThread.join();
            muxer.stop();
            MediaScannerConnection.scanFile(context,
                    new String[]{mFile.getAbsolutePath()}, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path, Uri uri) {
                            Log.i(LOGTAG, "MediaScanner scanned recording " + path);
                        }
                    });
        }
    }
}
