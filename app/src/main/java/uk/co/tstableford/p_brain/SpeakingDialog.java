package uk.co.tstableford.p_brain;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.widget.Button;

public class SpeakingDialog extends Dialog {
    public SpeakingDialog(final Activity activity) {
        super(activity);
        this.setContentView(R.layout.speaking_dialog);
        this.setTitle("Speaking");
        this.setCancelable(true);
        this.setCanceledOnTouchOutside(true);

        Button button = (Button) findViewById(R.id.speaking_dialog_dismiss);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }
}
