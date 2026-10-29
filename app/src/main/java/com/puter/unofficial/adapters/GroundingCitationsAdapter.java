package com.puter.unofficial.adapters;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.puter.unofficial.R;
import com.puter.unofficial.models.GroundingSource;

import java.util.ArrayList;
import java.util.List;

public class GroundingCitationsAdapter extends RecyclerView.Adapter<GroundingCitationsAdapter.ViewHolder> {

    private final List<GroundingSource> items = new ArrayList<>();

    public void setItems(List<GroundingSource> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_grounding_citation_chip, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GroundingSource source = items.get(position);
        String label = source.getTitle();
        if (label == null || label.isEmpty()) {
            label = source.getUrl();
            try {
                Uri uri = Uri.parse(source.getUrl());
                if (uri.getHost() != null) label = uri.getHost();
            } catch (Exception ignored) {}
        }
        holder.tvTitle.setText(label);

        holder.itemView.setOnClickListener(v -> {
            if (source.getUrl() != null && !source.getUrl().isEmpty()) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(source.getUrl()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    v.getContext().startActivity(intent);
                } catch (Exception ignored) {}
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;

        ViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvCitationTitle);
        }
    }
}
