package uk.co.tstableford.p_brain.service;

import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;

public class SpeechListener implements RecognitionListener {
    private static final String TAG = "PBrainSpeechListener";
    private static final int SPEECH_TIMEOUT = 20000; // 20 seconds in milliseconds.
    private SpeechTimeout timeout = null;
    private StateCallback stateCallback = null;
    public void onReadyForSpeech(Bundle params) {
        if (stateCallback != null) {
            stateCallback.onStart();
        }
        if (timeout != null) {
            timeout.cancel();
        }
        timeout = new SpeechTimeout();
        new android.os.Handler().postDelayed(timeout, SPEECH_TIMEOUT);
    }

    public void setStateCallback(StateCallback callback) {
        this.stateCallback = callback;
    }

    void cancel() {
        if (timeout != null) {
            timeout.cancel();
            timeout = null;
            if (stateCallback != null) {
                stateCallback.onEnd(null);
            }
        }
    }

    public void onBeginningOfSpeech() { }

    public void onRmsChanged(float rmsdB) { }

    public void onBufferReceived(byte[] buffer) { }

    public void onEndOfSpeech() { }

    public void onError(int error) {
        Log.i(TAG, "Speech recognition error " + error);
        if (timeout != null) {
            timeout.cancel();
            timeout = null;
        }
        if (stateCallback != null) {
            stateCallback.onEnd(null);
        }
    }

    public void onResults(Bundle results) {
        Log.i(TAG, "onResults " + results);
        if (timeout != null) {
            timeout.cancel();
            timeout = null;
            ArrayList data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (data != null && data.size() > 0) {
                if (stateCallback != null) {
                    stateCallback.onEnd((String) data.get(0));
                }
            }
        }
    }

    public void onPartialResults(Bundle results) { }

    public void onEvent(int eventType, Bundle params) { }

    public interface StateCallback {
        void onStart();
        void onEnd(String message);
    }

    private class SpeechTimeout implements Runnable {
        private boolean timeoutCancelled = false;
        @Override
        public void run() {
            if (!timeoutCancelled) {
                timeoutCancelled = true;
                timeout = null;
                if (stateCallback != null) {
                    stateCallback.onEnd(null);
                }
            }
        }

        void cancel() {
            timeoutCancelled = true;
        }
    }
}