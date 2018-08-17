package uk.co.tstableford.p_brain;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.io.InputStream;

public class ChatMessage {
    public enum UserType {
        OTHER, SELF, STATUS
    };

    private String messageText;
    private UserType userType;
    private String icon;

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public void setUserType(UserType userType) {
        this.userType = userType;
    }
    public String getMessageText() {
        return messageText;
    }

    public UserType getUserType() {
        return userType;
    }

    public void setIcon(String icon) { this.icon = icon; }
    public String getIcon() { return this.icon; }
}
