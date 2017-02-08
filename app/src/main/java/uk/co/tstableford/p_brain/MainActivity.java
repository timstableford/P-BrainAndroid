package uk.co.tstableford.p_brain;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;
import android.speech.tts.TextToSpeech;
import android.os.Build;
import android.speech.RecognizerIntent;
import android.content.ActivityNotFoundException;
import android.annotation.TargetApi;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String TAG = "PBrainMain";
    private static final int REQ_CODE_SPEECH_INPUT = 100;
    private static final int REQ_CREATE_TRAINING_DATA = 101;
    private EditText chatEditText1;
    private ArrayList<ChatMessage> chatMessages;
    private ImageView enterChatView1;
    private ChatListAdapter listAdapter;
    private Socket mSocket;
    private HotwordDetector hotwordDetector;
    private TextToSpeech tts;
    private String name;

    private void sendMessage(final String messageText, final ChatMessage.UserType userType) {
        if (messageText.trim().length() == 0) {
            return;
        }
        final ChatMessage message = new ChatMessage();
        message.setMessageText(messageText);
        message.setUserType(userType);
        chatMessages.add(message);

        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        JSONObject object = new JSONObject();
        if (mSocket == null) {
            localMessage(getString(R.string.server_not_connected));
        } else {
            try {
                object.put("text", messageText);
                mSocket.emit("ask", object);
            } catch (JSONException e) {
                Log.e(TAG, "Failed to add message to JSON.", e);
            }
        }
    }

    private void responseMessage(String text, boolean silent) {
        final ChatMessage message = new ChatMessage();
        message.setMessageText(text);
        message.setUserType(ChatMessage.UserType.OTHER);
        chatMessages.add(message);

        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }

        if (!silent) {
            speak(text);
        }
    }

    private void localMessage(final String messageText) {
        final ChatMessage message = new ChatMessage();
        message.setMessageText(messageText);
        message.setUserType(ChatMessage.UserType.STATUS);
        chatMessages.add(message);

        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (hotwordDetector != null) {
            hotwordDetector.destroy();
        }
        if (mSocket != null) {
            mSocket.disconnect();
            mSocket.close();
            mSocket.off();
            mSocket = null;
        }
        if (tts != null) {
            tts.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final String server = prefs.getString("server_address", null);
        if (server == null) {
            Toast.makeText(this, "Enter server address.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }

        if (mSocket == null) {
            try {
                mSocket = IO.socket(server);
                setupSocketListeners();
            } catch (URISyntaxException e) {
                Log.e(TAG, "Error connecting socket.io.", e);
                localMessage(getString(R.string.server_not_connected));
            }
        }

        if (mSocket != null) {
            mSocket.connect();
        }
        if (hotwordDetector != null) {
            hotwordDetector.startListening();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (hotwordDetector != null) {
            hotwordDetector.stopListening();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chatEditText1 = (EditText) findViewById(R.id.chat_edit_text1);
        enterChatView1 = (ImageView) findViewById(R.id.enter_chat1);

        chatMessages = new ArrayList<>();
        ListView chatListView = (ListView) findViewById(R.id.chat_list_view);
        listAdapter = new ChatListAdapter(chatMessages, this);
        chatListView.setAdapter(listAdapter);

        chatEditText1.setOnKeyListener(keyListener);

        enterChatView1.setOnClickListener(clickListener);

        chatEditText1.addTextChangedListener(watcher1);

        try {
            hotwordDetector = new HotwordDetector(this);
            hotwordDetector.setHotwordListener(new HotwordListener() {
                @Override
                public void onHotword() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            responseMessage("Yes?", false);
                            promptSpeechInput();
                        }
                    });
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialise hotword detector.", e);
        }
        
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = tts.setLanguage(Locale.US);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "This Language is not supported");
                    }
                } else {
                    Log.e("TTS", "Initilization Failed!");
                }
            }
        });
    }
    
    private void speak(String text){
        // Stop listening so it doesn't trigger itself.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            speakLollipop(text);
        } else {
            speakKitkat(text);
        }
    }
    
    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void speakKitkat(String text) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
    }
    
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void speakLollipop(String text) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);    
    }

    private EditText.OnKeyListener keyListener = new View.OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            // If the event is a key-down event on the "enter" button
            if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                    (keyCode == KeyEvent.KEYCODE_ENTER)) {
                // Perform action on key press
                EditText editText = (EditText) v;
                if (v == chatEditText1) {
                    sendMessage(editText.getText().toString(), ChatMessage.UserType.SELF);
                }
                chatEditText1.setText("");
                return true;
            }
            return false;
        }
    };

    private ImageView.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v == enterChatView1) {
                sendMessage(chatEditText1.getText().toString(), ChatMessage.UserType.SELF);
            }
            chatEditText1.setText("");
        }
    };

    private final TextWatcher watcher1 = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            if (editable.length() == 0) {
                enterChatView1.setImageResource(R.drawable.ic_chat_send);
            } else {
                enterChatView1.setImageResource(R.drawable.ic_chat_send_active);
            }
        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings_item: {
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            }
            case R.id.train_item: {
                Intent intent = new Intent(this, TrainActivity.class);
                intent.putExtra(TrainActivity.NAME_INTENT, name);
                startActivityForResult(intent, REQ_CREATE_TRAINING_DATA);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings_menu, menu);
        return true;
    }

    /**
     * Showing google speech input dialog
     * */
    private void promptSpeechInput() {
        hotwordDetector.stopListening();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.speech_prompt));
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    getString(R.string.speech_not_supported),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Receiving speech input
     * */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {
                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    sendMessage(result.get(0), ChatMessage.UserType.SELF);
                }
                hotwordDetector.startListening();
                break;
            }
            case REQ_CREATE_TRAINING_DATA: {
                if (resultCode == RESULT_OK) {
                    if (hotwordDetector.setKeyword(name)) {
                        localMessage(getString(R.string.keyword_update_success, name));
                    } else {
                        localMessage(getString(R.string.keyword_update_failure, name));
                    }
                }
            }

        }
    }

    private void setupSocketListeners() {
        mSocket.on("response", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        JSONObject data = (JSONObject) args[0];
                        try {
                            JSONObject msgObject = data.getJSONObject("msg");
                            String response = msgObject.getString("text");

                            if (msgObject.has("url")) {
                                String url = msgObject.getString("url");
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                            }

                            boolean silent = false;
                            if (msgObject.has("silent")) {
                                if (msgObject.getBoolean("silent")) {
                                    silent = true;
                                }
                            }
                            responseMessage(response, silent);
                        } catch (JSONException e) {
                            Log.e(TAG, "Error decoding JSON packet.", e);
                        }
                    }
                });
            }
        });

        mSocket.on("set_name", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        JSONObject data = (JSONObject) args[0];
                        try {
                            name = data.getString("name");
                            if (hotwordDetector != null) {
                                if (hotwordDetector.setKeyword(name)) {
                                    Log.i(TAG, "Keyword set to " + name);
                                } else {
                                    Toast.makeText(MainActivity.this, getString(R.string.missing_training_data, name), Toast.LENGTH_LONG).show();
                                    Intent intent = new Intent(MainActivity.this, TrainActivity.class);
                                    intent.putExtra(TrainActivity.NAME_INTENT, name);
                                    startActivityForResult(intent, REQ_CREATE_TRAINING_DATA);
                                }
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error decoding JSON packet.", e);
                        }
                    }
                });
            }
        });

        mSocket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        localMessage(getString(R.string.connected));
                    }
                });
            }
        });

        mSocket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        localMessage(getString(R.string.disconnected));
                    }
                });
            }
        });
    }
}
