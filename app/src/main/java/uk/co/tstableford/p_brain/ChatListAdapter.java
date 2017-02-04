package uk.co.tstableford.p_brain;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class ChatListAdapter extends BaseAdapter {
    private ArrayList<ChatMessage> chatMessages;
    private Context context;

    public ChatListAdapter(ArrayList<ChatMessage> chatMessages, Context context) {
        this.chatMessages = chatMessages;
        this.context = context;
    }

    @Override
    public int getCount() {
        return chatMessages.size();
    }

    @Override
    public Object getItem(int position) {
        return chatMessages.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = null;
        ChatMessage message = chatMessages.get(position);
        ViewHolder holder1;
        ViewHolder holder2;

        if (message.getUserType() == ChatMessage.UserType.SELF) {
            if (convertView == null) {
                v = LayoutInflater.from(context).inflate(R.layout.chat_item_outgoing, null, false);
                holder1 = new ViewHolder();
                holder1.messageTextView = (TextView) v.findViewById(R.id.message_text_outgoing);
                v.setTag(holder1);
            } else {
                v = convertView;
                holder1 = (ViewHolder) v.getTag();
            }
            holder1.messageTextView.setText(message.getMessageText());
        } else if (message.getUserType() == ChatMessage.UserType.OTHER) {
            if (convertView == null) {
                v = LayoutInflater.from(context).inflate(R.layout.chat_item_incoming, null, false);
                holder2 = new ViewHolder();
                holder2.messageTextView = (TextView) v.findViewById(R.id.message_text_incoming);
                v.setTag(holder2);
            } else {
                v = convertView;
                holder2 = (ViewHolder) v.getTag();
            }
            holder2.messageTextView.setText(message.getMessageText());
        }

        return v;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = chatMessages.get(position);
        return message.getUserType().ordinal();
    }

    private class ViewHolder {
        public TextView messageTextView;
    }
}
