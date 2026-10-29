package com.puter.unofficial.models;

import java.util.ArrayList;
import java.util.List;

public class ChatSession {
    private String id;
    private String title;
    private long createdAt;
    private long updatedAt;
    private String selectedModelId = "gpt-4o-mini";
    private GenerationConfig config = new GenerationConfig();
    private List<ChatMessage> messages = new ArrayList<>();

    public ChatSession(String id, String title, long createdAt) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getSelectedModelId() {
        return selectedModelId;
    }

    public void setSelectedModelId(String selectedModelId) {
        this.selectedModelId = selectedModelId;
    }

    public GenerationConfig getConfig() {
        return config;
    }

    public void setConfig(GenerationConfig config) {
        this.config = config != null ? config : new GenerationConfig();
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages != null ? messages : new ArrayList<>();
    }

    public void addMessage(ChatMessage message) {
        if (messages == null) messages = new ArrayList<>();
        messages.add(message);
        this.updatedAt = System.currentTimeMillis();
    }
}
