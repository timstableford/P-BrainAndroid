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

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String TAG = "PBrainMain";
    private EditText chatEditText1;
    private ArrayList<ChatMessage> chatMessages;
    private ImageView enterChatView1;
    private ChatListAdapter listAdapter;
    private Socket mSocket;
    private HotwordDetector hotwordDetector;

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
        try {
            object.put("text", messageText);
            mSocket.emit("ask", object);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to add message to JSON.", e);
        }
    }

    private void localMessage(final String messageText) {
        final ChatMessage message = new ChatMessage();
        message.setMessageText(messageText);
        message.setUserType(ChatMessage.UserType.OTHER);
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
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String server = prefs.getString("server_address", null);
        if (server == null) {
            Toast.makeText(this, "Enter server address.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }

        try {
            mSocket = IO.socket(server);
        } catch (URISyntaxException e) {
            Log.e(TAG, "Error connecting socket.io.", e);
        }

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

                            final ChatMessage message = new ChatMessage();
                            message.setMessageText(response);
                            message.setUserType(ChatMessage.UserType.OTHER);
                            chatMessages.add(message);

                            if (listAdapter != null) {
                                listAdapter.notifyDataSetChanged();
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error decoding JSON packet.", e);
                        }
                    }
                });
            }
        });

        mSocket.connect();
        if (hotwordDetector != null) {
            hotwordDetector.startListening();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        localMessage("Disconnecting...");
        if (mSocket != null) {
            mSocket.disconnect();
            mSocket.close();
            mSocket.off();
            mSocket = null;
        }
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
                            localMessage("Yes?");
                        }
                    });
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialise hotword detector.", e);
        }
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
            case R.id.settings_item:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings_menu, menu);
        return true;
    }
}
