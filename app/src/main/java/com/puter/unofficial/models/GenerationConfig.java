package com.puter.unofficial.models;

public class GenerationConfig {
    private String systemInstructions = "";
    private float temperature = 0.7f;
    private float topP = 0.95f;
    private int maxTokens = 4096;
    private String aspectRatio = "1:1";
    private boolean webGroundingEnabled = false;
    private String replyLanguage = "";

    public GenerationConfig() {}

    public String getSystemInstructions() {
        return systemInstructions;
    }

    public void setSystemInstructions(String systemInstructions) {
        this.systemInstructions = systemInstructions != null ? systemInstructions : "";
    }

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public float getTopP() {
        return topP;
    }

    public void setTopP(float topP) {
        this.topP = topP;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public String getAspectRatio() {
        return aspectRatio;
    }

    public void setAspectRatio(String aspectRatio) {
        this.aspectRatio = aspectRatio != null ? aspectRatio : "1:1";
    }

    public boolean isWebGroundingEnabled() {
        return webGroundingEnabled;
    }

    public void setWebGroundingEnabled(boolean webGroundingEnabled) {
        this.webGroundingEnabled = webGroundingEnabled;
    }

    public String getReplyLanguage() {
        return replyLanguage;
    }

    public void setReplyLanguage(String replyLanguage) {
        this.replyLanguage = replyLanguage != null ? replyLanguage : "";
    }
}
