package uk.co.tstableford.p_brain;

public class ChatMessage {
    public enum UserType {
        OTHER, SELF, STATUS
    };
    private String messageText;
    private UserType userType;

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
}
