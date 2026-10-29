package com.puter.unofficial.models;

import java.util.ArrayList;
import java.util.List;

public class ChatMessage {
    public static final String ROLE_USER = "User";
    public static final String ROLE_BOT = "Bot";
    public static final String ROLE_SYSTEM = "System";

    private String id;
    private String role;
    private String text;
    private long timestamp;
    private List<StagedFile> attachments = new ArrayList<>();
    private List<GroundingSource> groundingSources = new ArrayList<>();
    private boolean isError = false;
    private boolean isStreaming = false;
    private String modelUsed = "";
    private String generatedImageUrl = "";

    public ChatMessage(String id, String role, String text, long timestamp) {
        this.id = id;
        this.role = role;
        this.text = text;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public List<StagedFile> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<StagedFile> attachments) {
        this.attachments = attachments != null ? attachments : new ArrayList<>();
    }

    public List<GroundingSource> getGroundingSources() {
        return groundingSources;
    }

    public void setGroundingSources(List<GroundingSource> groundingSources) {
        this.groundingSources = groundingSources != null ? groundingSources : new ArrayList<>();
    }

    public boolean isError() {
        return isError;
    }

    public void setError(boolean error) {
        isError = error;
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public void setStreaming(boolean streaming) {
        isStreaming = streaming;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public String getGeneratedImageUrl() {
        return generatedImageUrl;
    }

    public void setGeneratedImageUrl(String generatedImageUrl) {
        this.generatedImageUrl = generatedImageUrl;
    }
}
