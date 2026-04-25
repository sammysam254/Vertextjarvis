package com.jarvis.assistant.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.jarvis.assistant.R;
import java.util.List;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

    public static class ConversationItem {
        public final String from;
        public final String text;
        public ConversationItem(String from, String text) {
            this.from = from;
            this.text = text;
        }
    }

    private static final int VIEW_USER = 0;
    private static final int VIEW_JARVIS = 1;
    private static final int VIEW_SYSTEM = 2;

    private final List<ConversationItem> items;

    public ConversationAdapter(List<ConversationItem> items) {
        this.items = items;
    }

    @Override
    public int getItemViewType(int position) {
        switch (items.get(position).from) {
            case "user": return VIEW_USER;
            case "jarvis": return VIEW_JARVIS;
            default: return VIEW_SYSTEM;
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout;
        switch (viewType) {
            case VIEW_USER: layout = R.layout.item_message_user; break;
            case VIEW_JARVIS: layout = R.layout.item_message_jarvis; break;
            default: layout = R.layout.item_message_system; break;
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvText;
        ViewHolder(View view) {
            super(view);
            tvText = view.findViewById(R.id.tv_message);
        }
        void bind(ConversationItem item) {
            if (tvText != null) tvText.setText(item.text);
        }
    }
}
