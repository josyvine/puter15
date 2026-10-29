package com.puter.unofficial.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.puter.unofficial.R;
import com.puter.unofficial.models.ChatSession;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SessionDrawerAdapter extends RecyclerView.Adapter<SessionDrawerAdapter.ViewHolder> {

    public interface OnSessionClickListener {
        void onSessionClick(ChatSession session);
        void onSessionDelete(ChatSession session);
    }

    private final List<ChatSession> sessions = new ArrayList<>();
    private final OnSessionClickListener listener;
    private String activeSessionId = null;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault());

    public SessionDrawerAdapter(OnSessionClickListener listener) {
        this.listener = listener;
    }

    public void setSessions(List<ChatSession> newSessions, String activeSessionId) {
        this.sessions.clear();
        if (newSessions != null) {
            this.sessions.addAll(newSessions);
        }
        this.activeSessionId = activeSessionId;
        notifyDataSetChanged();
    }

    public void setActiveSessionId(String activeSessionId) {
        this.activeSessionId = activeSessionId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_session_drawer_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatSession session = sessions.get(position);
        holder.tvTitle.setText(session.getTitle());
        holder.tvDate.setText(dateFormat.format(new Date(session.getUpdatedAt())));

        boolean isActive = session.getId().equals(activeSessionId);
        if (isActive) {
            holder.llRoot.setBackgroundResource(R.drawable.bg_pill_badge);
            holder.llRoot.setBackgroundColor(Color.parseColor("#e8f0fe"));
            holder.tvTitle.setTextColor(Color.parseColor("#1a73e8"));
        } else {
            holder.llRoot.setBackgroundResource(R.drawable.bg_pill_badge);
            holder.llRoot.setBackgroundColor(Color.parseColor("#ffffff"));
            holder.tvTitle.setTextColor(Color.parseColor("#333333"));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSessionClick(session);
        });

        // 800ms long-press confirmation
        holder.itemView.setOnLongClickListener(v -> {
            showDeleteDialog(v.getContext(), session);
            return true;
        });

        holder.btnDelete.setOnClickListener(v -> {
            showDeleteDialog(v.getContext(), session);
        });
    }

    private void showDeleteDialog(Context context, ChatSession session) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.delete_session_confirm)
                .setMessage(R.string.delete_session_msg)
                .setPositiveButton(R.string.btn_delete, (d, w) -> {
                    if (listener != null) listener.onSessionDelete(session);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout llRoot;
        TextView tvTitle;
        TextView tvDate;
        ImageView btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            llRoot = itemView.findViewById(R.id.llSessionRowRoot);
            tvTitle = itemView.findViewById(R.id.tvSessionTitle);
            tvDate = itemView.findViewById(R.id.tvSessionDate);
            btnDelete = itemView.findViewById(R.id.btnDeleteSession);
        }
    }
}
