package com.puter.unofficial;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.puter.unofficial.adapters.ModelAccordionAdapter;
import com.puter.unofficial.models.AIModelDefinition;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dedicated Model Catalog, Taxonomy, and Taxonomy Parser.
 * Handles local assets (models.json, gemini_models.json), remote cached JSONs,
 * category inference, and prepares items for ModelAccordionAdapter.
 */
public class ModelCatalogManager {

    private static final String TAG = "ModelCatalogManager";

    public interface ModelLoadCallback {
        void onModelsReady();
        void onRequestLivePuterSync();
    }

    private final Context context;
    private final List<AIModelDefinition> allModelsList = new ArrayList<>();
    private final Map<String, List<AIModelDefinition>> modelsByProvider = new HashMap<>();

    public ModelCatalogManager(Context context) {
        this.context = context;
    }

    public List<AIModelDefinition> getAllModelsList() {
        return allModelsList;
    }

    public Map<String, List<AIModelDefinition>> getModelsByProvider() {
        return modelsByProvider;
    }

    public AIModelDefinition findModelById(String modelId) {
        if (modelId == null) return null;
        for (AIModelDefinition m : allModelsList) {
            if (m.getId().equalsIgnoreCase(modelId)) {
                return m;
            }
        }
        return null;
    }

    public void loadModels(boolean isLiveMode, String activeProvider, ModelLoadCallback callback) {
        allModelsList.clear();
        modelsByProvider.clear();
        SharedPreferences prefs = context.getSharedPreferences(AppConstants.PREF_NAME, Context.MODE_PRIVATE);

        if ("Gemini".equalsIgnoreCase(activeProvider)) {
            if (isLiveMode) {
                String cached = prefs.getString(AppConstants.KEY_GEMINI_CACHED_LIVE_MODELS, null);
                if (cached != null && !cached.trim().isEmpty() && !cached.equals("{}") && !cached.equals("[]")) {
                    parseGeminiModelsJson(cached);
                } else {
                    loadLocalGeminiModels();
                }
            } else {
                loadLocalGeminiModels();
            }
            if (callback != null) callback.onModelsReady();
        } else {
            if (isLiveMode) {
                String cached = prefs.getString(AppConstants.KEY_PUTER_CACHED_LIVE_MODELS, null);
                if (cached != null && !cached.trim().isEmpty() && !cached.equals("{}") && !cached.equals("[]")) {
                    parsePuterModelsJson(cached);
                }

                if (AuthManager.getInstance(context).isLoggedIn()) {
                    if (callback != null) callback.onRequestLivePuterSync();
                } else {
                    if (allModelsList.isEmpty()) {
                        loadLocalPuterModels();
                    }
                }
            } else {
                loadLocalPuterModels();
            }
            if (callback != null) callback.onModelsReady();
        }
    }

    public void loadLocalGeminiModels() {
        try {
            InputStream is = context.getAssets().open(AppConstants.GEMINI_MODELS_JSON_FILE);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String jsonStr = new String(buffer, StandardCharsets.UTF_8);
            parseGeminiModelsJson(jsonStr);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load gemini_models.json: " + e.getMessage());
        }
    }

    public void loadLocalPuterModels() {
        try {
            InputStream is = context.getAssets().open(AppConstants.MODELS_JSON_FILE);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String jsonStr = new String(buffer, StandardCharsets.UTF_8);
            parsePuterModelsJson(jsonStr);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load models.json: " + e.getMessage());
        }
    }

    public void parseGeminiModelsJson(String jsonStr) {
        try {
            allModelsList.clear();
            modelsByProvider.clear();

            if (jsonStr.trim().startsWith("[")) {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    addGeminiModelNode(arr.getJSONObject(i), "Google Gemini");
                }
                return;
            }

            JSONObject root = new JSONObject(jsonStr);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String sectionKey = keys.next();
                JSONArray arr = root.optJSONArray(sectionKey);
                if (arr != null) {
                    String parentName = formatParentName(sectionKey);
                    if (!modelsByProvider.containsKey(parentName)) {
                        modelsByProvider.put(parentName, new ArrayList<>());
                    }
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        addGeminiModelNode(obj, parentName);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing Gemini models: " + e.getMessage());
        }
    }

    private void addGeminiModelNode(JSONObject obj, String parentGroup) {
        String rawId = obj.optString("id", obj.optString("name", "")).replace("models/", "");
        if (rawId.isEmpty()) return;
        String name = obj.optString("name", obj.optString("displayName", rawId));
        int ctxTokens = obj.optInt("context", 1000000);
        String ctxStr = (ctxTokens >= 1000) ? (ctxTokens / 1000) + "k" : String.valueOf(ctxTokens);

        List<String> cats = new ArrayList<>();
        JSONArray catArr = obj.optJSONArray("categories");
        if (catArr != null) {
            for (int c = 0; c < catArr.length(); c++) {
                cats.add(catArr.optString(c).toLowerCase(Locale.US));
            }
        } else {
            String desc = obj.optString("description", "").toLowerCase(Locale.US);
            String idLower = rawId.toLowerCase(Locale.US);
            String nameLower = name.toLowerCase(Locale.US);
            if (nameLower.contains("live preview") || idLower.contains("live-preview") ||
                nameLower.contains("native audio") || idLower.contains("native-audio")) {
                cats.add("live");
                cats.add("audio");
            } else if (idLower.contains("audio") || idLower.contains("tts") || idLower.contains("speech")) {
                cats.add("audio");
            }
            if (idLower.contains("image") || idLower.contains("banana") || idLower.contains("imagen")) {
                cats.add("image_generation");
            }
            if (idLower.contains("flash") || idLower.contains("pro") || desc.contains("vision")) {
                cats.add("vision");
                cats.add("multimodal");
            }
            if (cats.isEmpty()) cats.add("text_chat");
        }

        boolean isImg = cats.contains("image_generation");
        boolean isVis = cats.contains("vision") || cats.contains("multimodal");
        boolean isLiv = cats.contains("live");

        AIModelDefinition model = new AIModelDefinition(rawId, name, parentGroup, ctxStr, isVis, isImg, isLiv);
        model.setCategories(cats);
        allModelsList.add(model);
        if (!modelsByProvider.containsKey(parentGroup)) {
            modelsByProvider.put(parentGroup, new ArrayList<>());
        }
        modelsByProvider.get(parentGroup).add(model);
    }

    public void parsePuterModelsJson(String jsonStr) {
        try {
            allModelsList.clear();
            modelsByProvider.clear();

            if (jsonStr.trim().startsWith("[")) {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    addPuterModel(arr.getJSONObject(i), "Other");
                }
                return;
            }

            JSONObject root = new JSONObject(jsonStr);
            if (root.has("result") && root.optJSONArray("result") != null) {
                JSONArray arr = root.getJSONArray("result");
                for (int i = 0; i < arr.length(); i++) {
                    addPuterModel(arr.getJSONObject(i), "Other");
                }
                return;
            }

            if (root.has("models") && root.optJSONArray("models") != null) {
                JSONArray arr = root.getJSONArray("models");
                for (int i = 0; i < arr.length(); i++) {
                    addPuterModel(arr.getJSONObject(i), "Other");
                }
                return;
            }

            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String providerKey = keys.next();
                JSONArray arr = root.optJSONArray(providerKey);
                if (arr != null) {
                    String providerDisplay = capitalize(providerKey);
                    if (!modelsByProvider.containsKey(providerDisplay)) {
                        modelsByProvider.put(providerDisplay, new ArrayList<>());
                    }
                    for (int i = 0; i < arr.length(); i++) {
                        addPuterModel(arr.getJSONObject(i), providerDisplay);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing Puter models: " + e.getMessage());
        }
    }

    private void addPuterModel(JSONObject obj, String defaultProvider) {
        String id = obj.optString("id", obj.optString("name", ""));
        if (id.isEmpty()) return;
        String name = obj.optString("name", id);
        String provider = capitalize(obj.optString("provider", obj.optString("vendor", defaultProvider)));
        if (!modelsByProvider.containsKey(provider)) {
            modelsByProvider.put(provider, new ArrayList<>());
        }
        int ctx = obj.optInt("context", 128000);
        String ctxStr = (ctx >= 1000) ? (ctx / 1000) + "k" : String.valueOf(ctx);

        List<String> cats = new ArrayList<>();
        JSONArray catArr = obj.optJSONArray("categories");
        if (catArr != null && catArr.length() > 0) {
            for (int c = 0; c < catArr.length(); c++) {
                cats.add(catArr.optString(c).toLowerCase(Locale.US));
            }
        } else {
            cats = inferPuterCategories(id, name, obj.optString("description", ""));
        }

        boolean imgGen = cats.contains("image_generation");
        boolean vision = cats.contains("vision") || cats.contains("multimodal");

        AIModelDefinition m = new AIModelDefinition(id, name, provider, ctxStr, vision, imgGen, false);
        m.setCategories(cats);
        allModelsList.add(m);
        modelsByProvider.get(provider).add(m);
    }

    private List<String> inferPuterCategories(String id, String name, String desc) {
        String combined = (id + " " + name + " " + desc).toLowerCase(Locale.US);
        List<String> cats = new ArrayList<>();
        if (combined.contains("image") || combined.contains("dall-e") || combined.contains("flux") ||
                combined.contains("stable-diffusion") || combined.contains("imagen") || combined.contains("midjourney") ||
                combined.contains("txt2img") || combined.contains("banana")) {
            cats.add("image_generation");
        }
        if (combined.contains("vision") || combined.contains("vl") || combined.contains("4o") ||
                combined.contains("gemini") || combined.contains("multimodal") || combined.contains("omni")) {
            cats.add("vision");
            cats.add("multimodal");
        }
        if (combined.contains("coder") || combined.contains("code") || combined.contains("deepseek") ||
                combined.contains("starcoder") || combined.contains("qwen") || combined.contains("wizardcoder")) {
            cats.add("code");
        }
        if (combined.contains("reasoning") || combined.contains("thinking") || combined.contains("r1") ||
                combined.contains("o1") || combined.contains("o3")) {
            cats.add("reasoning");
        }
        if (combined.contains("live") || combined.contains("audio") || combined.contains("voice") || combined.contains("tts")) {
            cats.add("audio");
            cats.add("live");
        }
        if (cats.isEmpty() || !cats.contains("image_generation")) {
            cats.add("text_chat");
        }
        return cats;
    }

    public List<ModelAccordionAdapter.AccordionItem> buildAccordionItems(String activeModelId) {
        List<ModelAccordionAdapter.AccordionItem> items = new ArrayList<>();

        List<AIModelDefinition> sortedAll = new ArrayList<>(allModelsList);
        Collections.sort(sortedAll, (a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()));
        items.add(ModelAccordionAdapter.AccordionItem.createHeader("★ All Models", sortedAll.size()));
        for (AIModelDefinition m : sortedAll) {
            items.add(ModelAccordionAdapter.AccordionItem.createChild(m, "★ All Models"));
        }

        for (Map.Entry<String, List<AIModelDefinition>> entry : modelsByProvider.entrySet()) {
            String pName = "Parent: " + entry.getKey();
            items.add(ModelAccordionAdapter.AccordionItem.createHeader(pName, entry.getValue().size()));
            for (AIModelDefinition m : entry.getValue()) {
                items.add(ModelAccordionAdapter.AccordionItem.createChild(m, pName));
            }
        }

        Map<String, List<AIModelDefinition>> byCategory = new HashMap<>();
        for (AIModelDefinition m : allModelsList) {
            List<String> cats = m.getCategories();
            if (cats == null || cats.isEmpty()) cats = m.getCapabilityTags();
            for (String cat : cats) {
                String formatted = "Category: " + formatCategoryName(cat);
                if (!byCategory.containsKey(formatted)) {
                    byCategory.put(formatted, new ArrayList<>());
                }
                byCategory.get(formatted).add(m);
            }
        }
        for (Map.Entry<String, List<AIModelDefinition>> entry : byCategory.entrySet()) {
            items.add(ModelAccordionAdapter.AccordionItem.createHeader(entry.getKey(), entry.getValue().size()));
            for (AIModelDefinition m : entry.getValue()) {
                items.add(ModelAccordionAdapter.AccordionItem.createChild(m, entry.getKey()));
            }
        }

        return items;
    }

    public boolean isImageGenerationModel(String modelId) {
        if (modelId == null) return false;
        String mLower = modelId.toLowerCase(Locale.US);
        if (mLower.contains("banana") || mLower.contains("dall-e") || mLower.contains("flux") ||
            mLower.contains("imagen") || mLower.contains("midjourney") || mLower.contains("txt2img")) {
            return true;
        }
        for (AIModelDefinition def : allModelsList) {
            if (def.getId().equalsIgnoreCase(modelId)) {
                List<String> cats = def.getCategories();
                if (cats != null && (cats.contains("image_generation") || cats.contains("image"))) {
                    return true;
                }
            }
        }
        return PuterChatManager.isImageModel(modelId);
    }

    private String formatCategoryName(String cat) {
        if (cat == null) return "General";
        return cat.replace("_", " ").toUpperCase(Locale.US);
    }

    private String formatParentName(String raw) {
        if (raw == null || raw.isEmpty()) return "Other";
        return raw.replace("_", " ").toUpperCase(Locale.US);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase(Locale.US) + str.substring(1);
    }
}