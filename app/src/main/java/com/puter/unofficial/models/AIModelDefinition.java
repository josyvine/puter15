package com.puter.unofficial.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Model definition representing an AI model's metadata, taxonomy, capabilities,
 * and backend routing provider (Puter Network vs. Google Gemini Native).
 */
public class AIModelDefinition {
    private String id;
    private String displayName;
    private String provider;
    private String parentGroup;
    private String contextTokenLimit;
    private boolean supportsVision;
    private boolean supportsImageGen;
    private boolean isGeminiLive;
    private List<String> capabilityTags = new ArrayList<>();
    private List<String> categories = new ArrayList<>();

    public AIModelDefinition(String id, String displayName, String provider, String contextTokenLimit,
                             boolean supportsVision, boolean supportsImageGen, boolean isGeminiLive) {
        this.id = id;
        this.displayName = displayName;
        this.provider = provider;
        this.parentGroup = provider;
        this.contextTokenLimit = contextTokenLimit != null ? contextTokenLimit : "";
        this.supportsVision = supportsVision;
        this.supportsImageGen = supportsImageGen;
        this.isGeminiLive = isGeminiLive;

        // Auto-check for image generation keywords directly from ID / name
        String lowerCombined = (this.id + " " + this.displayName).toLowerCase(Locale.US);
        if (lowerCombined.contains("banana") || lowerCombined.contains("dall-e") || 
            lowerCombined.contains("flux") || lowerCombined.contains("imagen") || 
            lowerCombined.contains("midjourney") || lowerCombined.contains("txt2img")) {
            this.supportsImageGen = true;
        }

        if (this.supportsVision) addCapabilityTag("vision");
        if (this.supportsImageGen) addCapabilityTag("image_gen");
        if (this.isGeminiLive) addCapabilityTag("live");
    }

    public String getId() {
        return id != null ? id : "";
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        }
        return getId();
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getProvider() {
        return provider != null ? provider : "Other";
    }

    public void setProvider(String provider) {
        this.provider = provider;
        if (this.parentGroup == null || this.parentGroup.isEmpty()) {
            this.parentGroup = provider;
        }
    }

    public String getParentGroup() {
        if (parentGroup != null && !parentGroup.trim().isEmpty()) {
            return parentGroup;
        }
        return getProvider();
    }

    public void setParentGroup(String parentGroup) {
        this.parentGroup = parentGroup;
    }

    public String getContextTokenLimit() {
        return contextTokenLimit != null ? contextTokenLimit : "";
    }

    public void setContextTokenLimit(String contextTokenLimit) {
        this.contextTokenLimit = contextTokenLimit != null ? contextTokenLimit : "";
    }

    public boolean isSupportsVision() {
        return supportsVision;
    }

    public void setSupportsVision(boolean supportsVision) {
        this.supportsVision = supportsVision;
        if (supportsVision) addCapabilityTag("vision");
    }

    public boolean isSupportsImageGen() {
        return supportsImageGen;
    }

    public void setSupportsImageGen(boolean supportsImageGen) {
        this.supportsImageGen = supportsImageGen;
        if (supportsImageGen) addCapabilityTag("image_gen");
    }

    public boolean isGeminiLive() {
        return isGeminiLive;
    }

    public void setGeminiLive(boolean geminiLive) {
        isGeminiLive = geminiLive;
        if (geminiLive) addCapabilityTag("live");
    }

    public List<String> getCapabilityTags() {
        return capabilityTags;
    }

    public void setCapabilityTags(List<String> capabilityTags) {
        this.capabilityTags = capabilityTags != null ? capabilityTags : new ArrayList<>();
    }

    public List<String> getCategories() {
        return categories;
    }

    /**
     * Ingests capability categories and synchronizes feature flags and tags.
     */
    public void setCategories(List<String> categories) {
        this.categories = categories != null ? categories : new ArrayList<>();
        for (String cat : this.categories) {
            String lower = cat.toLowerCase(Locale.US);
            if (lower.contains("image") || lower.contains("imagen") || lower.contains("flux") || 
                lower.contains("txt2img") || lower.contains("banana") || lower.contains("dall-e")) {
                this.supportsImageGen = true;
                addCapabilityTag("image_gen");
            }
            if (lower.contains("vision") || lower.contains("multimodal") || lower.contains("vl")) {
                this.supportsVision = true;
                addCapabilityTag("vision");
            }
            if (lower.contains("live") || lower.contains("native-audio")) {
                this.isGeminiLive = true;
                addCapabilityTag("live");
            }
            if (lower.contains("code") || lower.contains("coder")) {
                addCapabilityTag("code");
            }
            if (lower.contains("reasoning") || lower.contains("thinking")) {
                addCapabilityTag("reasoning");
            }
        }
    }

    public boolean hasCategory(String category) {
        if (category == null || categories == null) return false;
        for (String c : categories) {
            if (c.equalsIgnoreCase(category)) return true;
        }
        return false;
    }

    public void addCategory(String category) {
        if (category == null || category.trim().isEmpty()) return;
        if (this.categories == null) this.categories = new ArrayList<>();
        if (!hasCategory(category)) {
            this.categories.add(category.trim().toLowerCase(Locale.US));
        }
    }

    public void addCapabilityTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) return;
        if (this.capabilityTags == null) this.capabilityTags = new ArrayList<>();
        String clean = tag.trim().toLowerCase(Locale.US);
        if (!this.capabilityTags.contains(clean)) {
            this.capabilityTags.add(clean);
        }
    }

    /**
     * Indicates whether this model runs through Puter's multi-model network.
     */
    public boolean isPuterBackend() {
        String lowerId = getId().toLowerCase(Locale.US);
        if (lowerId.contains("openrouter") || lowerId.contains("togetherai") || lowerId.contains("puter") || lowerId.contains(":")) {
            return true;
        }
        String parent = getParentGroup().toLowerCase(Locale.US);
        return !parent.contains("google gemini") && !parent.equals("gemini");
    }

    /**
     * Indicates whether this model is a direct Google Gemini API model.
     */
    public boolean isGeminiNativeBackend() {
        return !isPuterBackend();
    }
}