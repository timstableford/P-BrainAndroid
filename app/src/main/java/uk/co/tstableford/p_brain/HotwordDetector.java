package uk.co.tstableford.p_brain;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

import ai.kitt.snowboy.SnowboyDetect;

public class HotwordDetector {
    private static final String TAG = "HotwordDetector";
    private static final int RECORDER_SAMPLERATE = 16000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int ELEMENTS_TO_RECORD = 1024;
    private static final int BYTES_PER_ELEMENT = 2; // 2 bytes in 16bit format
    private SnowboyDetect detector;
    private AudioRecord recorder = null;
    private Thread recordingThread = null;
    private boolean isRecording = false;
    private HotwordListener hotwordListener = null;
    private String currentKeyword = null;
    private boolean isListening = false;

    public static void copy(InputStream in, OutputStream out) throws IOException {
        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    public static boolean isKeywordTrained(String keyword) {
        keyword = keyword.replaceAll(" ", "_").toLowerCase();
        File path = new File(Environment.getExternalStorageDirectory().getPath(), "pbrain");
        return new File(path, keyword + ".pmdl").exists();
    }

    public boolean setKeyword(String keyword) {
        keyword = keyword.replaceAll(" ", "_").toLowerCase();
        if (keyword.equals(currentKeyword)) {
            return true;
        }
        if (!isKeywordTrained(keyword)) {
            return false;
        }
        File path = new File(Environment.getExternalStorageDirectory().getPath(), "pbrain");
        boolean wasListening = isListening;
        if (isListening) {
            stopListening();
        }

        File common = new File(path, "common.res");
        File hotword = new File(path, keyword + ".pmdl");
        detector = new SnowboyDetect(common.getPath(), hotword.getPath());
        detector.SetSensitivity("0.5");
        detector.SetAudioGain(1);

        if (wasListening) {
            startListening();
        }
        currentKeyword = keyword;

        return true;
    }

    public HotwordDetector(Context context) throws IOException {
        File path = new File(Environment.getExternalStorageDirectory().getPath(), "pbrain");
        if (!path.exists()) {
            if (!path.mkdirs()) {
                throw new IOException("Failed to make: " + path.getPath());
            }
        }
        Log.i(TAG, "Getting models from: " + path);

        File common = new File(path, "common.res");
        {
            InputStream databaseInputStream = context.getResources().openRawResource(R.raw.common);
            FileOutputStream outputStream = new FileOutputStream(common);
            copy(databaseInputStream, outputStream);
        }

        File hotword = new File(path, "brain.pmdl");
        {
            InputStream databaseInputStream = context.getResources().openRawResource(R.raw.brain);
            FileOutputStream outputStream = new FileOutputStream(hotword);
            copy(databaseInputStream, outputStream);
        }
        currentKeyword = "brain";

        // Sets up Snowboy.
        detector = new SnowboyDetect(common.getPath(), hotword.getPath());
        detector.SetSensitivity("0.5");
        detector.SetAudioGain(1);
        Log.i(TAG, "Snowboy Setup");

        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING, ELEMENTS_TO_RECORD * BYTES_PER_ELEMENT);
    }

    public void startListening() {
        if (!isListening) {
            recorder.startRecording();
            isListening = true;
            if (!isRecording) {
                isRecording = true;
                recordingThread = new Thread(new Runnable() {
                    public void run() {
                        detectHotword();
                    }
                }, "AudioRecorder Thread");
                recordingThread.start();
            }
        }
    }

    public void stopListening() {
        // stops the recording activity
        if (null != recorder) {
            recorder.stop();
        }
        isListening = false;
    }

    public void destroy() {
        if (null != recorder) {
            isRecording = false;
            stopListening();
            recorder.release();
            try {
                recordingThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to join hotword thread.", e);
            }
            recordingThread = null;
        }
    }

    public void setHotwordListener(HotwordListener listener) {
        hotwordListener = listener;
    }

    //convert short to byte
    private byte[] short2byte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];
        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;

    }

    private void detectHotword() {
        short sData[] = new short[ELEMENTS_TO_RECORD];
        short[] snowboyData = new short[1024];

        while (isRecording) {
            // gets the voice output from microphone to byte format
            recorder.read(sData, 0, ELEMENTS_TO_RECORD);

            byte bData[] = short2byte(sData);
            // Converts bytes into int16 that Snowboy will read.
            ByteBuffer.wrap(bData).order(
                    ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(snowboyData);

            // Detection.
            int result = detector.RunDetection(snowboyData, snowboyData.length);
            if (result > 0 && hotwordListener != null) {
                hotwordListener.onHotword();
            }
        }
    }
}
