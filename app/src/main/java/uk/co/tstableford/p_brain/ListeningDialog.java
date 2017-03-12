package uk.co.tstableford.p_brain;

import android.app.Activity;
import android.app.Dialog;

public class ListeningDialog extends Dialog {
    public ListeningDialog(final Activity activity) {
        super(activity);
        this.setContentView(R.layout.listening_dialog);
        this.setTitle("Listening");
        this.setCancelable(true);
        this.setCanceledOnTouchOutside(true);
    }
}
