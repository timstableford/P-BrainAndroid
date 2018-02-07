package uk.co.tstableford.p_brain.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

import uk.co.tstableford.p_brain.ConnectionManager;
import uk.co.tstableford.p_brain.LoginDialog;
import uk.co.tstableford.p_brain.MainActivity;
import uk.co.tstableford.p_brain.R;
import uk.co.tstableford.p_brain.RetryDialog;
import uk.co.tstableford.p_brain.SettingsActivity;
import uk.co.tstableford.p_brain.TrainActivity;

public class LocalService extends Service {
    private static final int ONGOING_NOTIFICATION_ID = 0x3237877;
    private static final String TAG = "PBrainService";
    private VerbalUI verbalUi;
    private SharedPreferences preferences;
    private ConnectionManager.AuthListener validationListener;
    private String connectedServer = null;
    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    private Handler handler;
    private Socket socket;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public boolean authenticate() {
        final String server = preferences.getString("server_address", null);
        if (server == null) {
            return false;
        }
        final String token = preferences.getString("token", null);
        if (token == null) {
            return false;
        }
        final ConnectionManager manager = new ConnectionManager(server);
        manager.validateToken(token, validationListener);

        return true;
    }


    @Override
    public void onCreate() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification =
                new Notification.Builder(this)
                        .setContentTitle(getText(R.string.app_name))
                        .setContentText(getText(R.string.listening_hotword))
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentIntent(pendingIntent)
                        .setWhen(0)
                        .build();

        startForeground(ONGOING_NOTIFICATION_ID, notification);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        handler = new Handler();
        verbalUi = new VerbalUI(this, null);

        validationListener = new ConnectionManager.AuthListener() {
            @Override
            public void onSuccess(String token) {
                String server = preferences.getString("server_address", null);
                setupSocket(server, token);
            }

            @Override
            public void onFailure(String reason, int status) {
                Log.w(TAG, "Failed to authenticate token on server. " + reason);
            }
        };
    }

    private void setupSocket(String server, String token) {
        if (server == null) {
            return;
        }
        if (!server.equals(connectedServer) || socket == null) {
            teardownSocket();
            try {
                IO.Options opts = new IO.Options();
                opts.forceNew = true;
                opts.query = "token=" + token;
                socket = IO.socket(server, opts);
                setupSocketListeners();
                connectedServer = server;
                socket.connect();
            } catch (URISyntaxException e) {
                Log.e(TAG, "Error connecting socket.io.", e);
                connectedServer = null;
            }
        }
    }

    private void setupSocketListeners() {
        socket.on("response", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        JSONObject data = (JSONObject) args[0];
                        try {
                            JSONObject msgObject = data.getJSONObject("msg");
                            // Response will be what is spoken.
                            String response = msgObject.getString("text");
                            // Text will include the response and additional details such as URL.
                            String text = response;

                            if (msgObject.has("url")) {
                                String url = msgObject.getString("url");
                                text = text + " " + url;
                                if (msgObject.has("url_autolaunch")) {
                                    if (msgObject.getBoolean("url_autolaunch")) {
                                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                                    }
                                }
                            }

                            boolean silent = false;
                            if (msgObject.has("silent")) {
                                if (msgObject.getBoolean("silent")) {
                                    silent = true;
                                }
                            }
                            boolean canRespond = false;
                            if (msgObject.has("canRespond")) {
                                if (msgObject.getBoolean("canRespond")) {
                                    canRespond = true;
                                }
                            }
                            if (!silent && canRespond) {
                                promptForResponseOnSpeechEnd = true;
                            }

                            responseMessage(response, text, silent);
                        } catch (JSONException e) {
                            Log.e(TAG, "Error decoding JSON packet.", e);
                        }
                    }
                });
            }
        });

        socket.on("set_name", new Emitter.Listener() {
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
                                    statusMessage(getString(R.string.voice_prompt, name));
                                } else {
                                    hotwordDetector.stopListening();
                                    speechListener.cancel();
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

        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusMessage(getString(R.string.connected));
                    }
                });
            }
        });

        socket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusMessage(getString(R.string.disconnected));
                    }
                });
            }
        });
    }


    @Override
    public void onDestroy() {
        stopForeground(true);
        if (verbalUi != null) {
            verbalUi.destroy();
            verbalUi = null;
        }
        super.onDestroy();
    }

    public void runOnUiThread(Runnable runnable) {
        handler.post(runnable);
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        LocalService getService() {
            // Return this instance of LocalService so clients can call public methods
            return LocalService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}