package uk.co.tstableford.p_brain;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.widget.Button;

public class RetryDialog extends Dialog {
    public RetryDialog(final Activity activity,
                       final Response listener) {
        super(activity);
        this.setContentView(R.layout.retry_dialog);
        this.setTitle("Retry Connection?");
        this.setCancelable(false);
        this.setCanceledOnTouchOutside(false);

        // Init button of login GUI
        Button btnRetry = (Button) this.findViewById(R.id.btnRetry);
        Button btnCancel = (Button) this.findViewById(R.id.btnCancel);
        btnRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RetryDialog.this.dismiss();
                listener.onRetry();
            }
        });
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RetryDialog.this.dismiss();
                listener.onCancel();
            }
        });
    }

    public interface Response {
        void onRetry();
        void onCancel();
    }
}
