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
import androidx.recyclerview.widget.RecyclerView;

import com.puter.unofficial.R;
import com.puter.unofficial.models.AIModelDefinition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Hierarchical Accordion Adapter for AI Model Selection.
 * Provides categorization by Parent provider, capability tags, and clean visual status.
 */
public class ModelAccordionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHILD = 1;

    public interface OnModelSelectedListener {
        void onModelSelected(AIModelDefinition model);
    }

    public static class AccordionItem {
        public boolean isHeader;
        public String categoryName;
        public int childCount;
        public boolean isExpanded;
        public AIModelDefinition model;

        public static AccordionItem createHeader(String name, int count) {
            AccordionItem item = new AccordionItem();
            item.isHeader = true;
            item.categoryName = name;
            item.childCount = count;
            item.isExpanded = false;
            return item;
        }

        public static AccordionItem createChild(AIModelDefinition model, String categoryName) {
            AccordionItem item = new AccordionItem();
            item.isHeader = false;
            item.categoryName = categoryName;
            item.model = model;
            return item;
        }
    }

    private final List<AccordionItem> displayList = new ArrayList<>();
    private final List<AccordionItem> masterList = new ArrayList<>();
    private final Set<String> expandedCategories = new HashSet<>();
    private String selectedModelId = "gpt-4o-mini";
    private final OnModelSelectedListener listener;

    public ModelAccordionAdapter(OnModelSelectedListener listener) {
        this.listener = listener;
    }

    public void setData(List<AccordionItem> items, String selectedModelId) {
        this.masterList.clear();
        if (items != null) this.masterList.addAll(items);
        this.selectedModelId = selectedModelId;

        // Auto-expand the category containing the active model on first load
        if (expandedCategories.isEmpty() && selectedModelId != null && !selectedModelId.isEmpty()) {
            for (AccordionItem it : this.masterList) {
                if (!it.isHeader && it.model != null && isModelMatch(it.model, selectedModelId)) {
                    expandedCategories.add(it.categoryName);
                    break;
                }
            }
        }

        rebuildDisplayList();
    }

    public void setSelectedModelId(String modelId) {
        this.selectedModelId = modelId;
        notifyDataSetChanged();
    }

    private void rebuildDisplayList() {
        displayList.clear();
        for (AccordionItem item : masterList) {
            if (item.isHeader) {
                item.isExpanded = expandedCategories.contains(item.categoryName);
                displayList.add(item);
            } else {
                if (expandedCategories.contains(item.categoryName)) {
                    displayList.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return displayList.get(position).isHeader ? TYPE_HEADER : TYPE_CHILD;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View v = inflater.inflate(R.layout.item_model_category_header, parent, false);
            return new HeaderViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_model_child_item, parent, false);
            return new ChildViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AccordionItem item = displayList.get(position);
        if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder h = (HeaderViewHolder) holder;
            h.tvTitle.setText(item.categoryName);
            h.tvCount.setText(String.format(Locale.US, "(%d)", item.childCount));
            h.ivChevron.setImageResource(item.isExpanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);

            h.itemView.setOnClickListener(v -> {
                if (expandedCategories.contains(item.categoryName)) {
                    expandedCategories.remove(item.categoryName);
                } else {
                    expandedCategories.add(item.categoryName);
                }
                rebuildDisplayList();
            });
        } else if (holder instanceof ChildViewHolder) {
            ChildViewHolder c = (ChildViewHolder) holder;
            AIModelDefinition m = item.model;
            if (m == null) return;

            // 1. Display Name with fallback
            String displayName = m.getDisplayName();
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = m.getId();
            }
            c.tvName.setText(displayName);

            // 2. Format Categories as clean subtitles e.g. "(text_chat, vision)"
            List<String> cats = m.getCategories();
            if (cats != null && !cats.isEmpty()) {
                StringBuilder catStr = new StringBuilder("(");
                for (int i = 0; i < cats.size(); i++) {
                    catStr.append(cats.get(i).replace("_", " "));
                    if (i < cats.size() - 1) catStr.append(", ");
                }
                catStr.append(")");
                c.tvContext.setText(catStr.toString());
                c.tvContext.setVisibility(View.VISIBLE);
            } else if (m.getContextTokenLimit() != null && !m.getContextTokenLimit().trim().isEmpty()) {
                c.tvContext.setText("(" + m.getContextTokenLimit() + " ctx)");
                c.tvContext.setVisibility(View.VISIBLE);
            } else {
                c.tvContext.setText("(unknown)");
                c.tvContext.setVisibility(View.VISIBLE);
            }

            // 3. Resilient Selection State Matching
            boolean isSelected = isModelMatch(m, selectedModelId);
            c.ivCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            c.tvName.setTextColor(isSelected ? Color.parseColor("#1a73e8") : Color.parseColor("#333333"));
            c.tvContext.setTextColor(isSelected ? Color.parseColor("#5b93d9") : Color.parseColor("#777777"));

            // 4. Populate capability pill badges cleanly
            c.llTags.removeAllViews();
            Context ctx = c.itemView.getContext();
            List<String> tags = new ArrayList<>(m.getCapabilityTags());

            // Ensure verified capabilities are included even if missing from raw tags
            if (m.isSupportsImageGen() && !tags.contains("image_gen")) tags.add("image_gen");
            if (m.isSupportsVision() && !tags.contains("vision")) tags.add("vision");
            if (m.isGeminiLive() && !tags.contains("live")) tags.add("live");

            for (String tag : tags) {
                if (tag.equalsIgnoreCase("text_chat") || tag.equalsIgnoreCase("unknown")) {
                    continue; // Keep badges reserved for standout capabilities
                }
                TextView tagView = new TextView(ctx);
                tagView.setText(tag.replace("_", " "));
                tagView.setTextSize(9.5f);
                tagView.setTextColor(Color.parseColor("#1a73e8"));
                tagView.setBackgroundResource(R.drawable.bg_pill_badge);
                tagView.setPadding(12, 3, 12, 3);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMarginEnd(8);
                tagView.setLayoutParams(lp);
                c.llTags.addView(tagView);
            }

            c.itemView.setOnClickListener(v -> {
                selectedModelId = m.getId();
                notifyDataSetChanged();
                if (listener != null) listener.onModelSelected(m);
            });
        }
    }

    private boolean isModelMatch(AIModelDefinition def, String targetId) {
        if (def == null || targetId == null) return false;
        if (def.getId().equalsIgnoreCase(targetId)) return true;
        if (def.getDisplayName().equalsIgnoreCase(targetId)) return true;

        String normDef = def.getId().replace(":", "/").toLowerCase(Locale.US);
        String normTarget = targetId.replace(":", "/").toLowerCase(Locale.US);
        return normDef.equalsIgnoreCase(normTarget);
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvCount;
        ImageView ivChevron;

        HeaderViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvCategoryTitle);
            tvCount = itemView.findViewById(R.id.tvCategoryCount);
            ivChevron = itemView.findViewById(R.id.ivCategoryChevron);
        }
    }

    static class ChildViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvContext;
        LinearLayout llTags;
        ImageView ivCheck;

        ChildViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvModelDisplayName);
            tvContext = itemView.findViewById(R.id.tvModelContextLimit);
            llTags = itemView.findViewById(R.id.llModelTags);
            ivCheck = itemView.findViewById(R.id.ivModelSelectedCheck);
        }
    }
}