package com.puter.unofficial.adapters;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.puter.unofficial.R;
import com.puter.unofficial.logging.LogEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RecyclerView adapter for the System Log Console.
 * Binds deep diagnostic cards with colored status badges, live search filtering,
 * expandable untruncated telemetry, and single-tap copy utilities.
 */
public class LogConsoleAdapter extends RecyclerView.Adapter<LogConsoleAdapter.LogViewHolder> {

    private final List<LogEntry> masterList = new ArrayList<>();
    private final List<LogEntry> displayList = new ArrayList<>();
    private final Set<String> expandedLogIds = new HashSet<>();

    private String currentSearchQuery = "";
    private String currentCategoryFilter = "ALL";

    public void setLogs(List<LogEntry> logs) {
        masterList.clear();
        if (logs != null) {
            masterList.addAll(logs);
        }
        applyFilter();
    }

    public void addEntry(LogEntry entry) {
        if (entry != null) {
            masterList.add(0, entry);
            if (entry.matches(currentSearchQuery, currentCategoryFilter)) {
                displayList.add(0, entry);
                notifyItemInserted(0);
            }
        }
    }

    public void setFilterQuery(String query) {
        this.currentSearchQuery = query != null ? query : "";
        applyFilter();
    }

    public void setCategoryFilter(String category) {
        this.currentCategoryFilter = category != null ? category : "ALL";
        applyFilter();
    }

    public void clearLogs() {
        masterList.clear();
        displayList.clear();
        expandedLogIds.clear();
        notifyDataSetChanged();
    }

    private void applyFilter() {
        displayList.clear();
        for (LogEntry entry : masterList) {
            if (entry.matches(currentSearchQuery, currentCategoryFilter)) {
                displayList.add(entry);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_system_log_entry, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        LogEntry entry = displayList.get(position);
        Context context = holder.itemView.getContext();

        // 1. Level Badge and Tint
        holder.tvLogLevel.setText(entry.getLevelBadgeText());
        int badgeColor;
        switch (entry.getLevel()) {
            case OK:
                badgeColor = Color.parseColor("#00E676"); // Vibrant Mint Green
                break;
            case WARN:
                badgeColor = Color.parseColor("#FFAB00"); // Alert Orange
                break;
            case ERROR:
                badgeColor = Color.parseColor("#FF5252"); // High-priority Red
                break;
            case CRASH:
                badgeColor = Color.parseColor("#D50000"); // Crimson
                break;
            case PROCESS:
                badgeColor = Color.parseColor("#E040FB"); // Process Violet
                break;
            case INFO:
            default:
                badgeColor = Color.parseColor("#448AFF"); // Tech Blue
                break;
        }
        holder.tvLogLevel.setTextColor(badgeColor);

        // 2. Tag and Millisecond Timestamp
        holder.tvLogTag.setText(entry.getTag());
        holder.tvLogTime.setText(entry.getFormattedTime());

        // 3. Primary Title
        holder.tvLogTitle.setText(entry.getTitle());

        // 4. Untruncated Technical Details
        String details = entry.getDetails();
        boolean hasDetails = details != null && !details.trim().isEmpty();
        boolean isExpanded = expandedLogIds.contains(entry.getId());

        if (hasDetails) {
            holder.tvLogDetails.setText(details);
            holder.tvLogDetails.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            holder.tvExpandPrompt.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
            holder.tvExpandPrompt.setText("Tap to view untruncated telemetry (" + details.length() + " chars)");
        } else {
            holder.tvLogDetails.setVisibility(View.GONE);
            holder.tvExpandPrompt.setVisibility(View.GONE);
        }

        // 5. Expand / Collapse on Click
        holder.itemView.setOnClickListener(v -> {
            if (hasDetails) {
                if (isExpanded) {
                    expandedLogIds.remove(entry.getId());
                } else {
                    expandedLogIds.add(entry.getId());
                }
                notifyItemChanged(holder.getBindingAdapterPosition());
            }
        });

        // 6. Long-press to Copy Single Entry
        holder.itemView.setOnLongClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData clip = ClipData.newPlainText("Log Entry", entry.toClipboardFormat());
                cm.setPrimaryClip(clip);
                Toast.makeText(context, "Log entry copied to clipboard", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    public int getMasterCount() {
        return masterList.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView tvLogLevel;
        TextView tvLogTag;
        TextView tvLogTime;
        TextView tvLogTitle;
        TextView tvExpandPrompt;
        TextView tvLogDetails;

        LogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLogLevel = itemView.findViewById(R.id.tvLogLevel);
            tvLogTag = itemView.findViewById(R.id.tvLogTag);
            tvLogTime = itemView.findViewById(R.id.tvLogTime);
            tvLogTitle = itemView.findViewById(R.id.tvLogTitle);
            tvExpandPrompt = itemView.findViewById(R.id.tvExpandPrompt);
            tvLogDetails = itemView.findViewById(R.id.tvLogDetails);
        }
    }
}