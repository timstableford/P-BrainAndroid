package uk.co.tstableford.p_brain;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class LoginDialog extends Dialog {
    public LoginDialog(final Activity activity,
                       final String server,
                       final ConnectionManager.AuthListener listener) {
        super(activity);
        this.setContentView(R.layout.login_dialog);
        this.setTitle("Login");
        this.setCancelable(false);
        this.setCanceledOnTouchOutside(false);

        // Init button of login GUI
        Button btnLogin = (Button) this.findViewById(R.id.btnLogin);
        Button btnSettings = (Button) this.findViewById(R.id.btnSettings);
        final EditText txtUsername = (EditText) this.findViewById(R.id.txtUsername);
        final EditText txtPassword = (EditText) this.findViewById(R.id.txtPassword);

        // Attached listener for login GUI button
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(txtUsername.getText().toString().trim().length() > 0
                        && txtPassword.getText().toString().trim().length() > 0) {
                    ConnectionManager manager = new ConnectionManager(activity, server);
                    manager.login(txtUsername.getText().toString(), txtPassword.getText().toString(),
                            new ConnectionManager.AuthListener() {
                        @Override
                        public void onSuccess(String token) {
                            Toast.makeText(activity,
                                    "Login Successful", Toast.LENGTH_LONG).show();
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    LoginDialog.this.dismiss();
                                }
                            });
                            listener.onSuccess(token);
                        }

                        @Override
                        public void onFailure(String reason, int status) {
                            Toast.makeText(activity,
                                    "Login Failed: " + reason, Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    Toast.makeText(activity,
                            "Please enter Username and Password", Toast.LENGTH_LONG).show();
                }
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LoginDialog.this.dismiss();
                Intent intent = new Intent(activity, SettingsActivity.class);
                activity.startActivity(intent);
            }
        });
    }
}
