package uk.co.tstableford.p_brain;

import android.app.Activity;
import android.app.ProgressDialog;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

public class ConnectionManager {
    private static final String TAG = "ConnectionManager";
    private static final String LOGIN_API = "/api/login";
    private static final String VALIDATE_API = "/api/validate?token=";
    private Activity activity;
    private String server;
    private boolean isConnecting = false;

    public interface AuthListener {
        void onSuccess(String token);
        void onFailure(String reason, int status);
    }

    public ConnectionManager(Activity activity, String server) {
        this.activity = activity;
        this.server = server;
    }

    private int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) {
            result++;
        }
        return result;
    }

    private OkHttpClient buildClient(final String username, final String password) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.authenticator(new Authenticator() {
            @Nullable
            @Override
            public Request authenticate(Route route, Response response) throws IOException {
                if (responseCount(response) >= 3) {
                    return null; // If we've failed 3 times, give up. - in real life, never give up!!
                }

                String credential = Credentials.basic(username, password);
                return response.request().newBuilder().header("Authorization", credential).build();
            }
        });
        builder.connectTimeout(10, TimeUnit.SECONDS);
        builder.writeTimeout(10, TimeUnit.SECONDS);
        builder.readTimeout(30, TimeUnit.SECONDS);

        return builder.build();
    }

    public void login(final String username, final String password, final AuthListener listener) {
        if (isConnecting) {
            Toast.makeText(activity, "Already connecting.", Toast.LENGTH_SHORT).show();
            return;
        }
        isConnecting = true;

        final ProgressDialog dialog = ProgressDialog.show(activity, "Validating Token", "Please wait...");
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        OkHttpClient client = buildClient(username, password);
        Request request = new Request.Builder()
                .url(server + LOGIN_API)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                isConnecting = false;
                Log.e(TAG, "LOGIN request failed.", e);
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        listener.onFailure(e.getMessage(), 0);
                    }
                });
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                isConnecting = false;
                final String tokenJson = response.body().string();
                if (response.code() != 200) {
                    Log.e(TAG, tokenJson);
                }

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        String status = Integer.toString(response.code());
                        switch (response.code()) {
                            case 0:
                                status = "Timeout";
                                break;
                            case 401:
                                status = "Not authorized";
                                break;
                            case 404:
                                status = "API not found";
                                break;
                            default:
                                break;
                        }
                        if (response.code() == 200) {
                            try {
                                JSONObject obj = new JSONObject(tokenJson);
                                listener.onSuccess(obj.getString("token"));
                            } catch (JSONException e) {
                                listener.onFailure("Failed to parse response from server.", response.code());
                            }
                        } else {
                            listener.onFailure(status, response.code());
                        }
                    }
                });
            }
        });
    }

    public void validateToken(final String token, final AuthListener listener) {
        if (token == null || token.length() == 0) {
            listener.onFailure("Token is invalid.", 403);
            return;
        }
        if (isConnecting) {
            Toast.makeText(activity, "Already connecting.", Toast.LENGTH_SHORT).show();
            return;
        }
        isConnecting = true;
        final ProgressDialog dialog = ProgressDialog.show(activity, "Validating Token", "Please wait...");
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(server + VALIDATE_API + token)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                isConnecting = false;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        listener.onFailure(e.getMessage(), 0);
                    }
                });
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                isConnecting = false;
                if (response.code() != 200) {
                    Log.e(TAG, response.body().string());
                }
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        String status;
                        switch (response.code()) {
                            case 0:
                                status = "Connection timeout";
                                break;
                            case 401:
                                status = "Not authorized";
                                break;
                            case 404:
                                status = "API not found";
                                break;
                            case 503:
                                status = "Internal server error";
                                break;
                            default:
                                status = Integer.toString(response.code());
                                break;
                        }
                        if (response.code() == 200) {
                            listener.onSuccess(token);
                        } else {
                            listener.onFailure(status, response.code());
                        }
                    }
                });
            }
        });
    }
}
