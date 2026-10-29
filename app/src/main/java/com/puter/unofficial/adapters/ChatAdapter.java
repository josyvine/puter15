package com.puter.unofficial.adapters;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.puter.unofficial.CodeSyntaxHighlighter;
import com.puter.unofficial.NativeMarkdownParser;
import com.puter.unofficial.R;
import com.puter.unofficial.models.ChatMessage;
import com.puter.unofficial.models.StagedFile;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int VIEW_TYPE_USER = 1;
    public static final int VIEW_TYPE_ASSISTANT = 2;
    public static final int VIEW_TYPE_SYSTEM = 3;

    public interface OnChatMessageActionListener {
        void onCopyMessage(ChatMessage message);
        void onSpeakMessage(ChatMessage message);
        void onSpeakAssistantClicked(ChatMessage message, View speakContainer, TextView s1, TextView s2, TextView s3);
        void onRegenerateMessage(ChatMessage message);
        void onDeleteMessage(ChatMessage message);
        void onEditMessage(ChatMessage message);
        void onQuoteAndReattach(ChatMessage message);
        void onPreviewCode(String language, String code);
        void onPreviewImage(String imageUrlOrBase64);
        void onLongPressMessage(ChatMessage message, View view);

        default void onLongPressMessage(ChatMessage message, View view, float rawX, float rawY) {
            onLongPressMessage(message, view);
        }
    }

    private final List<ChatMessage> messages = new ArrayList<>();
    private final OnChatMessageActionListener actionListener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public ChatAdapter(OnChatMessageActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void setMessages(List<ChatMessage> newMessages) {
        messages.clear();
        if (newMessages != null) {
            messages.addAll(newMessages);
        }
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage message) {
        if (message != null) {
            messages.add(message);
            notifyItemInserted(messages.size() - 1);
        }
    }

    public void updateLastMessage(String appendText) {
        if (!messages.isEmpty()) {
            int lastIndex = messages.size() - 1;
            ChatMessage last = messages.get(lastIndex);
            last.setText(last.getText() + appendText);
            notifyItemChanged(lastIndex);
        }
    }

    public void setLastMessageComplete(String fullText) {
        if (!messages.isEmpty()) {
            int lastIndex = messages.size() - 1;
            ChatMessage last = messages.get(lastIndex);
            last.setText(fullText);
            last.setStreaming(false);
            notifyItemChanged(lastIndex);
        }
    }

    public void removeMessage(ChatMessage message) {
        int index = messages.indexOf(message);
        if (index >= 0) {
            messages.remove(index);
            notifyItemRemoved(index);
        }
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage msg = messages.get(position);
        if (ChatMessage.ROLE_USER.equalsIgnoreCase(msg.getRole())) {
            return VIEW_TYPE_USER;
        } else if (ChatMessage.ROLE_BOT.equalsIgnoreCase(msg.getRole())) {
            return VIEW_TYPE_ASSISTANT;
        } else {
            return VIEW_TYPE_SYSTEM;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_USER) {
            View v = inflater.inflate(R.layout.item_chat_user, parent, false);
            return new UserViewHolder(v);
        } else if (viewType == VIEW_TYPE_ASSISTANT) {
            View v = inflater.inflate(R.layout.item_chat_assistant, parent, false);
            return new AssistantViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_chat_system, parent, false);
            return new SystemViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        Context context = holder.itemView.getContext();

        // Coordinate-aware touch listener
        OnSwipeTouchListener touchListener = new OnSwipeTouchListener(context) {
            @Override
            public void onSwipeLeft() {
                if (actionListener != null) {
                    actionListener.onQuoteAndReattach(message);
                }
            }
        };
        holder.itemView.setOnTouchListener(touchListener);

        // Consume single tap so it NEVER triggers the context menu accidentally
        holder.itemView.setOnClickListener(v -> {
            // Intentionally consume single-click event
        });

        // Long-touch with exact touch coordinates (persists menu strictly on long press)
        holder.itemView.setOnLongClickListener(v -> {
            if (actionListener != null) {
                float rx = touchListener.getLastRawX();
                float ry = touchListener.getLastRawY();
                if (rx == 0 && ry == 0) {
                    int[] loc = new int[2];
                    v.getLocationOnScreen(loc);
                    rx = loc[0] + (v.getWidth() / 2f);
                    ry = loc[1] + (v.getHeight() / 2f);
                }
                actionListener.onLongPressMessage(message, v, rx, ry);
                return true;
            }
            return false;
        });

        if (holder instanceof UserViewHolder) {
            UserViewHolder uHolder = (UserViewHolder) holder;
            uHolder.tvMessage.setText(message.getText());
            uHolder.tvTimestamp.setText(timeFormat.format(new Date(message.getTimestamp())));

            // Attachments Preview
            if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
                uHolder.llAttachments.setVisibility(View.VISIBLE);
                uHolder.llAttachments.removeAllViews();
                for (StagedFile file : message.getAttachments()) {
                    ImageView iv = new ImageView(context);
                    int size = (int) (40 * context.getResources().getDisplayMetrics().density);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                    lp.setMarginEnd(8);
                    iv.setLayoutParams(lp);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setBackgroundResource(R.drawable.edit_text_border);
                    if (file.getThumbnailBitmap() != null) {
                        iv.setImageBitmap(file.getThumbnailBitmap());
                    } else if (file.getBase64DataUri() != null) {
                        loadBase64Image(iv, file.getBase64DataUri());
                    }
                    iv.setOnClickListener(v -> {
                        if (actionListener != null && file.getBase64DataUri() != null) {
                            actionListener.onPreviewImage(file.getBase64DataUri());
                        }
                    });
                    uHolder.llAttachments.addView(iv);
                }
            } else {
                uHolder.llAttachments.setVisibility(View.GONE);
            }

            uHolder.btnCopy.setOnClickListener(v -> {
                copyToClipboard(context, message.getText());
                if (actionListener != null) actionListener.onCopyMessage(message);
            });

            uHolder.btnEdit.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onEditMessage(message);
            });

        } else if (holder instanceof AssistantViewHolder) {
            AssistantViewHolder aHolder = (AssistantViewHolder) holder;
            aHolder.tvBadge.setText(message.getModelUsed() != null && !message.getModelUsed().isEmpty()
                    ? message.getModelUsed() : "Bot");
            aHolder.tvTimestamp.setText(timeFormat.format(new Date(message.getTimestamp())));

            // Parse Markdown and extract Code Blocks
            NativeMarkdownParser.ParsedMarkdown parsed = NativeMarkdownParser.extractCodeBlocks(message.getText());
            NativeMarkdownParser.renderToTextView(aHolder.tvMessage, parsed.mainText);

            // Populate Code Blocks
            aHolder.llCodeBlocks.removeAllViews();
            if (!parsed.codeBlocks.isEmpty()) {
                for (NativeMarkdownParser.ExtractedCodeBlock block : parsed.codeBlocks) {
                    View codeView = LayoutInflater.from(context).inflate(R.layout.item_chat_code_block, aHolder.llCodeBlocks, false);
                    TextView tvLang = codeView.findViewById(R.id.tvCodeLanguage);
                    TextView tvCode = codeView.findViewById(R.id.tvCodeContent);
                    TextView btnPreview = codeView.findViewById(R.id.btnPreviewCode);
                    TextView btnToggle = codeView.findViewById(R.id.btnToggleCodeVisibility);
                    TextView btnCopy = codeView.findViewById(R.id.btnCopyCode);
                    View scrollContainer = codeView.findViewById(R.id.hsvCodeScroll);

                    tvLang.setText(block.language);
                    tvCode.setText(CodeSyntaxHighlighter.highlight(block.code, block.language));

                    boolean isPreviewable = block.language.equalsIgnoreCase("html") ||
                            block.language.equalsIgnoreCase("xml") ||
                            block.language.equalsIgnoreCase("svg");
                    btnPreview.setVisibility(isPreviewable ? View.VISIBLE : View.GONE);
                    btnPreview.setOnClickListener(v -> {
                        if (actionListener != null) actionListener.onPreviewCode(block.language, block.code);
                    });

                    btnToggle.setOnClickListener(v -> {
                        boolean isVisible = scrollContainer.getVisibility() == View.VISIBLE;
                        scrollContainer.setVisibility(isVisible ? View.GONE : View.VISIBLE);
                        btnToggle.setText(isVisible ? "Reveal" : "Hide");
                    });

                    btnCopy.setOnClickListener(v -> {
                        copyToClipboard(context, block.code);
                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show();
                    });

                    aHolder.llCodeBlocks.addView(codeView);
                }
            }

            // Generated Image Preview if any
            if (message.getGeneratedImageUrl() != null && !message.getGeneratedImageUrl().isEmpty()) {
                aHolder.ivGeneratedImage.setVisibility(View.VISIBLE);
                aHolder.btnDownloadImage.setVisibility(View.VISIBLE);
                loadBase64Image(aHolder.ivGeneratedImage, message.getGeneratedImageUrl());

                aHolder.ivGeneratedImage.setOnClickListener(v -> {
                    if (actionListener != null) actionListener.onPreviewImage(message.getGeneratedImageUrl());
                });
                aHolder.btnDownloadImage.setOnClickListener(v -> {
                    if (actionListener != null) actionListener.onPreviewImage(message.getGeneratedImageUrl());
                });
            } else {
                aHolder.ivGeneratedImage.setVisibility(View.GONE);
                aHolder.btnDownloadImage.setVisibility(View.GONE);
            }

            // Grounding Citations
            if (message.getGroundingSources() != null && !message.getGroundingSources().isEmpty()) {
                aHolder.rvCitations.setVisibility(View.VISIBLE);
                aHolder.rvCitations.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
                GroundingCitationsAdapter cAdapter = new GroundingCitationsAdapter();
                cAdapter.setItems(message.getGroundingSources());
                aHolder.rvCitations.setAdapter(cAdapter);
            } else {
                aHolder.rvCitations.setVisibility(View.GONE);
            }

            // Actions
            aHolder.btnCopy.setOnClickListener(v -> {
                copyToClipboard(context, message.getText());
                if (actionListener != null) actionListener.onCopyMessage(message);
            });

            aHolder.btnSpeak.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onSpeakAssistantClicked(message, aHolder.flSpeakAssistantContainer,
                            aHolder.orbitStar1, aHolder.orbitStar2, aHolder.orbitStar3);
                }
            });

            aHolder.btnRegenerate.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onRegenerateMessage(message);
            });

            aHolder.btnDelete.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onDeleteMessage(message);
            });

        } else if (holder instanceof SystemViewHolder) {
            SystemViewHolder sHolder = (SystemViewHolder) holder;
            sHolder.tvStatus.setText(message.getText());
        }
    }

    private void copyToClipboard(Context context, String text) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Copied Text", text);
        if (cm != null) cm.setPrimaryClip(clip);
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void loadBase64Image(ImageView iv, String base64) {
        try {
            String clean = base64.replaceFirst("^data:image/[a-zA-Z]+;base64,", "");
            byte[] decoded = Base64.decode(clean, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
            iv.setImageBitmap(bmp);
        } catch (Exception ignored) {}
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage;
        TextView tvTimestamp;
        LinearLayout llAttachments;
        ImageView btnCopy;
        ImageView btnEdit;

        UserViewHolder(View v) {
            super(v);
            tvMessage = v.findViewById(R.id.tvUserMessage);
            tvTimestamp = v.findViewById(R.id.tvUserTimestamp);
            llAttachments = v.findViewById(R.id.llUserAttachmentsPreview);
            btnCopy = v.findViewById(R.id.btnCopyUserPrompt);
            btnEdit = v.findViewById(R.id.btnEditUserPrompt);
        }
    }

    static class AssistantViewHolder extends RecyclerView.ViewHolder {
        TextView tvBadge;
        TextView tvMessage;
        TextView tvTimestamp;
        ImageView ivGeneratedImage;
        ImageView btnDownloadImage;
        LinearLayout llCodeBlocks;
        RecyclerView rvCitations;
        View flSpeakAssistantContainer;
        ImageView btnCopy;
        ImageView btnSpeak;
        ImageView btnRegenerate;
        ImageView btnDelete;
        TextView orbitStar1, orbitStar2, orbitStar3;

        AssistantViewHolder(View v) {
            super(v);
            tvBadge = v.findViewById(R.id.tvBotBadge);
            tvMessage = v.findViewById(R.id.tvAssistantMessage);
            tvTimestamp = v.findViewById(R.id.tvAssistantTimestamp);
            ivGeneratedImage = v.findViewById(R.id.ivGeneratedImage);
            btnDownloadImage = v.findViewById(R.id.btnDownloadImageTiny);
            llCodeBlocks = v.findViewById(R.id.llCodeBlocksContainer);
            rvCitations = v.findViewById(R.id.rvGroundingCitations);
            flSpeakAssistantContainer = v.findViewById(R.id.flSpeakAssistantContainer);
            btnCopy = v.findViewById(R.id.btnCopyAssistant);
            btnSpeak = v.findViewById(R.id.btnSpeakAssistant);
            btnRegenerate = v.findViewById(R.id.btnRegenerateAssistant);
            btnDelete = v.findViewById(R.id.btnDeleteTurn);
            orbitStar1 = v.findViewById(R.id.orbitStar1);
            orbitStar2 = v.findViewById(R.id.orbitStar2);
            orbitStar3 = v.findViewById(R.id.orbitStar3);
        }
    }

    static class SystemViewHolder extends RecyclerView.ViewHolder {
        TextView tvStatus;
        Button btnRetry;

        SystemViewHolder(View v) {
            super(v);
            tvStatus = v.findViewById(R.id.tvSystemStatusText);
            btnRetry = v.findViewById(R.id.btnSystemRetry);
        }
    }

    /**
     * Touch helper detecting horizontal right-to-left swipe (>55px) and tracking raw touch coordinates.
     */
    public static class OnSwipeTouchListener implements View.OnTouchListener {
        private float downX;
        private float downY;
        private float lastRawX;
        private float lastRawY;
        private final float density;

        public OnSwipeTouchListener(Context context) {
            this.density = context.getResources().getDisplayMetrics().density;
        }

        public float getLastRawX() {
            return lastRawX;
        }

        public float getLastRawY() {
            return lastRawY;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    lastRawX = event.getRawX();
                    lastRawY = event.getRawY();
                    return false;
                case MotionEvent.ACTION_UP:
                    float upX = event.getX();
                    float upY = event.getY();
                    float deltaX = upX - downX;
                    float deltaY = upY - downY;
                    float threshold = 55 * density;
                    if (Math.abs(deltaX) > Math.abs(deltaY) && deltaX < -threshold) {
                        onSwipeLeft();
                        return true;
                    }
                    return false;
            }
            return false;
        }

        public void onSwipeLeft() {}
    }
}