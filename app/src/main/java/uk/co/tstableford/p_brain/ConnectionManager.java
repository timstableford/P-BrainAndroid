package uk.co.tstableford.p_brain;

import android.app.ProgressDialog;
import android.content.Context;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.json.JSONException;
import org.json.JSONObject;

import cz.msebera.android.httpclient.Header;

public class ConnectionManager {
    private static final String LOGIN_API = "/api/login";
    private static final String VALIDATE_API = "/api/validate?token=";
    private Context context;
    private String server;
    private boolean isConnecting = false;

    public interface AuthListener {
        void onSuccess(String token);
        void onFailure(String reason, int status);
    }

    public ConnectionManager(Context context, String server) {
        this.context = context;
        this.server = server;
    }

    public void login(String username, String password, final AuthListener listener) {
        if (isConnecting) {
            Toast.makeText(context, "Already connecting.", Toast.LENGTH_SHORT).show();
            return;
        }
        isConnecting = true;
        AsyncHttpClient client = new AsyncHttpClient();
        client.setBasicAuth(username, password);

        final ProgressDialog dialog = ProgressDialog.show(context, "Validating Token", "Please wait...");
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        client.get(context, server + LOGIN_API, new AsyncHttpResponseHandler() {
            @Override
            public void onStart() { }
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                isConnecting = false;
                dialog.dismiss();
                String tokenJson = new String(response);
                try {
                    JSONObject obj = new JSONObject(tokenJson);
                    listener.onSuccess(obj.getString("token"));
                } catch (JSONException e) {
                    listener.onFailure("Failed to parse response from server.", statusCode);
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                isConnecting = false;
                dialog.dismiss();
                String status = Integer.toString(statusCode);
                switch(statusCode) {
                    case 0:
                        status = "Timeout";
                        break;
                    case 401:
                        status = "Not authorized";
                        break;
                    case 404:
                        status = "API not found";
                        break;
                }
                listener.onFailure(status, statusCode);
            }
            @Override
            public void onRetry(int retryNo) { }
        });
    }

    public void validateToken(final String token, final AuthListener listener) {
        if (token == null || token.length() == 0) {
            listener.onFailure("Token is invalid.", 403);
            return;
        }
        if (isConnecting) {
            Toast.makeText(context, "Already connecting.", Toast.LENGTH_SHORT).show();
            return;
        }
        isConnecting = true;
        final ProgressDialog dialog = ProgressDialog.show(context, "Validating Token", "Please wait...");
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        AsyncHttpClient client = new AsyncHttpClient();
        client.get(context, server + VALIDATE_API + token, new AsyncHttpResponseHandler() {
            @Override
            public void onStart() { }
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                isConnecting = false;
                dialog.dismiss();
                listener.onSuccess(token);
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                isConnecting = false;
                dialog.dismiss();
                String status;
                switch(statusCode) {
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
                        status = Integer.toString(statusCode);
                }
                listener.onFailure(status, statusCode);
            }
            @Override
            public void onRetry(int retryNo) { }
        });
    }
}
