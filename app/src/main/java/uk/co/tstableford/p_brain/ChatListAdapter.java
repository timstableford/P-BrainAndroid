package uk.co.tstableford.p_brain;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.ImageLoader;

import java.util.ArrayList;

public class ChatListAdapter extends BaseAdapter {
    private static final int NUM_TYPES = 3;
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
        ViewHolder holder;

        if (message.getUserType() == ChatMessage.UserType.SELF) {
            if (convertView == null) {
                v = LayoutInflater.from(context).inflate(R.layout.chat_item_outgoing, null, false);
                holder = new ViewHolder();
                holder.messageTextView = (TextView) v.findViewById(R.id.message_text_outgoing);
                v.setTag(holder);
            } else {
                v = convertView;
                holder = (ViewHolder) v.getTag();
            }
            holder.messageTextView.setText(message.getMessageText());
        } else if (message.getUserType() == ChatMessage.UserType.OTHER) {
            if (convertView == null) {
                v = LayoutInflater.from(context).inflate(R.layout.chat_item_incoming, null, false);
                holder = new ViewHolder();
                holder.messageTextView = (TextView) v.findViewById(R.id.message_text_incoming);
                holder.imageView = (ImageView) v.findViewById(R.id.message_incoming_icon);
                v.setTag(holder);
            } else {
                v = convertView;
                holder = (ViewHolder) v.getTag();
            }
            if (message.getIcon() != null) {
                ImageLoader imageLoader = ImageLoader.getInstance();
                imageLoader.displayImage(message.getIcon(), holder.imageView);
                holder.imageView.setVisibility(View.VISIBLE);
            }
            holder.messageTextView.setText(message.getMessageText());
        } else if (message.getUserType() == ChatMessage.UserType.STATUS) {
            if (convertView == null) {
                v = LayoutInflater.from(context).inflate(R.layout.chat_item_status, null, false);
                holder = new ViewHolder();
                holder.messageTextView = (TextView) v.findViewById(R.id.message_text_status);
                v.setTag(holder);
            } else {
                v = convertView;
                holder = (ViewHolder) v.getTag();
            }
            holder.messageTextView.setText(message.getMessageText());
        }

        return v;
    }

    @Override
    public int getViewTypeCount() {
        return NUM_TYPES;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = chatMessages.get(position);
        return message.getUserType().ordinal();
    }

    private class ViewHolder {
        public TextView messageTextView;
        public ImageView imageView;
    }
}
