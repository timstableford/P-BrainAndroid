package uk.co.tstableford.p_brain.service;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.io.IOException;
import java.util.Locale;

import uk.co.tstableford.p_brain.HotwordDetector;
import uk.co.tstableford.p_brain.HotwordListener;

public class VerbalUI {
    private SharedPreferences preferences;
    private HotwordDetector hotwordDetector;
    private SpeechRecognizer speechRecognizer;
    private SpeechListener speechListener;
    private TextToSpeech tts;
    private State state = State.IDLE;

    private static final String TAG = "VerbalUI";

    public void destroy() {
        if (hotwordDetector != null) {
            hotwordDetector.destroy();
            hotwordDetector = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    public VerbalUI(final LocalService context, final String name) {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechListener = new SpeechListener();
        speechListener.setStateCallback(new SpeechListener.StateCallback() {
            @Override
            public void onStart() {
                Log.d(TAG, "speechListener: onStart");
            }

            @Override
            public void onEnd(String message) {
                Log.d(TAG, "speechListener: onStart");
                if (message != null) {
                    setState(State.IDLE);
                    // TODO send query to server
                } else {
                    setState(State.IDLE);
                }
            }
        });
        speechRecognizer.setRecognitionListener(speechListener);

        tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = tts.setLanguage(Locale.getDefault());
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "This Language is not supported");
                    }
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String utteranceId) {}

                        @Override
                        public void onDone(String utteranceId) {
                            context.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    setState(State.LISTENING_HOTWORD);
                                }
                            });
                        }

                        @Override
                        public void onError(String utteranceId) {
                            context.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Log.e(TAG, "Speech error");
                                    setState(State.LISTENING_HOTWORD);
                                }
                            });
                        }
                    });
                } else {
                    Log.e("TTS", "Initilization Failed!");
                }
            }
        });

        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean hotwordEnabled = preferences.getBoolean("hotword_enabled", true);
        if (hotwordEnabled && hotwordDetector == null) {
            try {
                hotwordDetector = new HotwordDetector(context);
                if (name != null) {
                    hotwordDetector.setKeyword(name);
                }
                hotwordDetector.setHotwordListener(new HotwordListener() {
                    @Override
                    public void onHotword() {
                        context.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setState(State.LISTENING_SPEECH);
                            }
                        });
                    }
                });
            } catch (IOException e) {
                Log.e(TAG, "Failed to initialise hotword detector.", e);
            }
        } else if (!hotwordEnabled && hotwordDetector != null) {
            hotwordDetector.destroy();
            hotwordDetector = null;
        }

        setState(State.LISTENING_HOTWORD);
    }

    public boolean setHotword(String hotword) {
        if (hotword != null) {
            return hotwordDetector.setKeyword(hotword);
        }
        return false;
    }

    public void setState(State state) {
        if (this.state == state) {
            return;
        }
        switch (this.state) {
            case IDLE:
                // Nothing to do when switching from IDLE.
                break;
            case LISTENING_HOTWORD:
                if (hotwordDetector != null) {
                    hotwordDetector.stopListening();
                }
                break;
            case LISTENING_SPEECH:
                if (speechListener != null) {
                    speechListener.cancel();
                }
                break;
            case SPEAKING:
                tts.stop();
                break;
            default:
                Log.w(TAG, "Unknown case in switch");
                break;
        }
        if (state == State.IDLE && preferences.getBoolean("hotword_enabled", true)) {
            this.state = State.LISTENING_HOTWORD;
        } else {
            this.state = state;
        }

        switch (this.state) {
            case IDLE:
                // Nothing to enable when switching to idle.
                break;
            case LISTENING_HOTWORD:
                if (preferences.getBoolean("hotword_enabled", true)) {
                    if (hotwordDetector != null) {
                        hotwordDetector.startListening();
                    } else {
                        Log.w(TAG, "Tried to start hotword recognition when not initialised.");
                    }
                } else {
                    this.state = State.IDLE;
                }
                break;
            case LISTENING_SPEECH: {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "uk.co.tstableford.p_brain");
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
                intent.putExtra("android.speech.extra.DICTATION_MODE", true);

                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                speechRecognizer.startListening(intent);
                break;
            }
            case SPEAKING:
                // Nothing to do here, the thread starting the speech should do the work.
                // TODO implement the function for speaking
                break;
            default:
                Log.w(TAG, "Unknown case in switch");
                break;
        }
    }

    public enum State {
        LISTENING_HOTWORD, // When listening for the hotword.
        LISTENING_SPEECH, // When Google Speech API is listening.
        SPEAKING, // When we're speaking.
        IDLE // If hotword recognition is disabled.
    }
}
