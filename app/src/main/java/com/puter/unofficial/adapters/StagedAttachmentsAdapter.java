package com.puter.unofficial.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.puter.unofficial.R;
import com.puter.unofficial.models.StagedFile;

import java.util.ArrayList;
import java.util.List;

public class StagedAttachmentsAdapter extends RecyclerView.Adapter<StagedAttachmentsAdapter.ViewHolder> {

    public interface OnRemoveAttachmentListener {
        void onRemove(int position, StagedFile file);
    }

    private final List<StagedFile> items = new ArrayList<>();
    private final OnRemoveAttachmentListener removeListener;

    public StagedAttachmentsAdapter(OnRemoveAttachmentListener removeListener) {
        this.removeListener = removeListener;
    }

    public void setItems(List<StagedFile> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void addAttachment(StagedFile file) {
        if (file != null) {
            items.add(file);
            notifyItemInserted(items.size() - 1);
        }
    }

    public void removeAttachment(int position) {
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    public List<StagedFile> getItems() {
        return items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_staged_attachment, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StagedFile file = items.get(position);
        if (file.isImage() && file.getThumbnailBitmap() != null) {
            holder.ivThumbnail.setImageBitmap(file.getThumbnailBitmap());
            holder.ivThumbnail.setVisibility(View.VISIBLE);
            holder.tvFileExt.setVisibility(View.GONE);
        } else {
            holder.ivThumbnail.setImageDrawable(null);
            holder.ivThumbnail.setVisibility(View.VISIBLE);
            holder.tvFileExt.setVisibility(View.VISIBLE);

            String ext = "FILE";
            if (file.getFilename() != null && file.getFilename().contains(".")) {
                ext = file.getFilename().substring(file.getFilename().lastIndexOf(".") + 1).toUpperCase();
                if (ext.length() > 4) ext = ext.substring(0, 4);
            }
            holder.tvFileExt.setText(ext);
        }

        holder.btnRemove.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && removeListener != null) {
                removeListener.onRemove(pos, file);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail;
        TextView tvFileExt;
        FrameLayout btnRemove;

        ViewHolder(View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.ivStagedThumbnail);
            tvFileExt = itemView.findViewById(R.id.tvStagedFileExt);
            btnRemove = itemView.findViewById(R.id.btnRemoveStagedFile);
        }
    }
}
