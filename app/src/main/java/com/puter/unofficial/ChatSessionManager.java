package com.puter.unofficial;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.puter.unofficial.models.ChatMessage;
import com.puter.unofficial.models.ChatSession;
import com.puter.unofficial.models.GroundingSource;
import com.puter.unofficial.models.StagedFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatSessionManager {
    private static final String PREF_SESSIONS = "PuterChatSessions";
    private static final String KEY_SESSION_IDS = "session_ids_index";
    private static final String KEY_CURRENT_SESSION_ID = "current_session_id";
    private static ChatSessionManager instance;

    private final SharedPreferences prefs;

    private ChatSessionManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_SESSIONS, Context.MODE_PRIVATE);
    }

    public static synchronized ChatSessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new ChatSessionManager(context);
        }
        return instance;
    }

    public synchronized ChatSession createNewSession(String defaultModelId) {
        String sessionId = "session_" + System.currentTimeMillis();
        ChatSession session = new ChatSession(sessionId, "New Chat", System.currentTimeMillis());
        if (defaultModelId != null && !defaultModelId.isEmpty()) {
            session.setSelectedModelId(defaultModelId);
        }
        saveSession(session);
        setCurrentSessionId(sessionId);
        return session;
    }

    public String getCurrentSessionId() {
        return prefs.getString(KEY_CURRENT_SESSION_ID, null);
    }

    public void setCurrentSessionId(String sessionId) {
        prefs.edit().putString(KEY_CURRENT_SESSION_ID, sessionId).apply();
    }

    public synchronized void saveSession(ChatSession session) {
        if (session == null || session.getId() == null) return;
        try {
            long now = System.currentTimeMillis();
            session.setUpdatedAt(now);

            JSONObject obj = new JSONObject();
            obj.put("id", session.getId());
            obj.put("title", session.getTitle());
            obj.put("createdAt", session.getCreatedAt());
            obj.put("updatedAt", session.getUpdatedAt());
            obj.put("selectedModelId", session.getSelectedModelId());

            if (session.getConfig() != null) {
                JSONObject cfg = new JSONObject();
                cfg.put("systemInstructions", session.getConfig().getSystemInstructions());
                cfg.put("temperature", session.getConfig().getTemperature());
                cfg.put("topP", session.getConfig().getTopP());
                cfg.put("maxTokens", session.getConfig().getMaxTokens());
                cfg.put("aspectRatio", session.getConfig().getAspectRatio());
                cfg.put("webGroundingEnabled", session.getConfig().isWebGroundingEnabled());
                cfg.put("replyLanguage", session.getConfig().getReplyLanguage());
                obj.put("config", cfg);
            }

            JSONArray msgs = new JSONArray();
            if (session.getMessages() != null) {
                for (ChatMessage m : session.getMessages()) {
                    if (m == null) continue;
                    JSONObject mj = new JSONObject();
                    mj.put("id", m.getId());
                    mj.put("role", m.getRole());
                    mj.put("text", m.getText() != null ? m.getText() : "");
                    mj.put("timestamp", m.getTimestamp());
                    mj.put("isError", m.isError());
                    mj.put("modelUsed", m.getModelUsed() != null ? m.getModelUsed() : "");
                    mj.put("generatedImageUrl", m.getGeneratedImageUrl() != null ? m.getGeneratedImageUrl() : "");

                    // Save citations
                    if (m.getGroundingSources() != null && !m.getGroundingSources().isEmpty()) {
                        JSONArray gArr = new JSONArray();
                        for (GroundingSource g : m.getGroundingSources()) {
                            if (g == null) continue;
                            JSONObject gj = new JSONObject();
                            gj.put("url", g.getUrl());
                            gj.put("title", g.getTitle());
                            gj.put("snippet", g.getSnippet());
                            gj.put("query", g.getSearchQuery());
                            gArr.put(gj);
                        }
                        mj.put("citations", gArr);
                    }

                    // Save staged attachments for persistent multi-turn media context
                    if (m.getAttachments() != null && !m.getAttachments().isEmpty()) {
                        JSONArray attArr = new JSONArray();
                        for (StagedFile file : m.getAttachments()) {
                            if (file == null) continue;
                            JSONObject fj = new JSONObject();
                            fj.put("filename", file.getFilename());
                            fj.put("mimeType", file.getMimeType());
                            fj.put("dataUri", file.getBase64DataUri());
                            fj.put("fileSize", file.getSizeBytes());
                            attArr.put(fj);
                        }
                        mj.put("attachments", attArr);
                    }

                    msgs.put(mj);
                }
            }
            obj.put("messages", msgs);

            prefs.edit().putString("sess_" + session.getId(), obj.toString()).apply();
            addSessionIdToIndex(session.getId());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized ChatSession loadSession(String sessionId) {
        if (sessionId == null) return null;
        String raw = prefs.getString("sess_" + sessionId, null);
        if (raw == null) return null;
        try {
            JSONObject obj = new JSONObject(raw);
            ChatSession session = new ChatSession(
                    obj.getString("id"),
                    obj.optString("title", "Chat"),
                    obj.optLong("createdAt", System.currentTimeMillis())
            );
            session.setUpdatedAt(obj.optLong("updatedAt", session.getCreatedAt()));
            session.setSelectedModelId(obj.optString("selectedModelId", "gemini-2.0-flash"));

            if (obj.has("config")) {
                JSONObject cfg = obj.getJSONObject("config");
                session.getConfig().setSystemInstructions(cfg.optString("systemInstructions", ""));
                session.getConfig().setTemperature((float) cfg.optDouble("temperature", 0.7));
                session.getConfig().setTopP((float) cfg.optDouble("topP", 0.95));
                session.getConfig().setMaxTokens(cfg.optInt("maxTokens", 4096));
                session.getConfig().setAspectRatio(cfg.optString("aspectRatio", "1:1"));
                session.getConfig().setWebGroundingEnabled(cfg.optBoolean("webGroundingEnabled", false));
                session.getConfig().setReplyLanguage(cfg.optString("replyLanguage", ""));
            }

            if (obj.has("messages")) {
                JSONArray msgs = obj.getJSONArray("messages");
                List<ChatMessage> list = new ArrayList<>();
                for (int i = 0; i < msgs.length(); i++) {
                    JSONObject mj = msgs.getJSONObject(i);
                    ChatMessage m = new ChatMessage(
                            mj.optString("id", "msg_" + i),
                            mj.optString("role", ChatMessage.ROLE_USER),
                            mj.optString("text", ""),
                            mj.optLong("timestamp", System.currentTimeMillis())
                    );
                    m.setError(mj.optBoolean("isError", false));
                    m.setModelUsed(mj.optString("modelUsed", ""));
                    m.setGeneratedImageUrl(mj.optString("generatedImageUrl", ""));
                    m.setStreaming(false);

                    // Restore citations
                    if (mj.has("citations")) {
                        JSONArray gArr = mj.getJSONArray("citations");
                        List<GroundingSource> gsList = new ArrayList<>();
                        for (int j = 0; j < gArr.length(); j++) {
                            JSONObject gj = gArr.getJSONObject(j);
                            gsList.add(new GroundingSource(
                                    gj.optString("url"),
                                    gj.optString("title"),
                                    gj.optString("snippet"),
                                    gj.optString("query")
                            ));
                        }
                        m.setGroundingSources(gsList);
                    }

                    // Restore attachments
                    if (mj.has("attachments")) {
                        JSONArray attArr = mj.getJSONArray("attachments");
                        List<StagedFile> fileList = new ArrayList<>();
                        for (int j = 0; j < attArr.length(); j++) {
                            JSONObject fj = attArr.getJSONObject(j);
                            String fname = fj.optString("filename", "attachment");
                            String mime = fj.optString("mimeType", "application/octet-stream");
                            String dataUri = fj.optString("dataUri", null);
                            long fsize = fj.optLong("fileSize", 0);

                            Uri uri = (dataUri != null) ? Uri.parse(dataUri) : null;
                            StagedFile sf = new StagedFile(uri, fname, mime, fsize, dataUri, null);
                            fileList.add(sf);
                        }
                        m.setAttachments(fileList);
                    }

                    list.add(m);
                }
                session.setMessages(list);
            }
            return session;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public synchronized List<ChatSession> getAllSessions() {
        List<String> ids = getSessionIdsIndex();
        List<ChatSession> list = new ArrayList<>();
        for (String id : ids) {
            ChatSession s = loadSession(id);
            if (s != null) list.add(s);
        }
        Collections.sort(list, (a, b) -> Long.compare(b.getUpdatedAt(), a.getUpdatedAt()));
        return list;
    }

    public synchronized void deleteSession(String sessionId) {
        if (sessionId == null) return;
        // Permanently erase session memory and messages from disk
        prefs.edit().remove("sess_" + sessionId).apply();

        List<String> ids = getSessionIdsIndex();
        ids.remove(sessionId);
        saveSessionIdsIndex(ids);

        if (sessionId.equals(getCurrentSessionId())) {
            setCurrentSessionId(null);
        }
    }

    public synchronized void autoUpdateTitleIfNeeded(ChatSession session, String firstPrompt) {
        if (session == null || firstPrompt == null) return;
        if ("New Chat".equals(session.getTitle()) || session.getTitle().isEmpty()) {
            String title = firstPrompt.trim().replaceAll("\\s+", " ");
            if (title.length() > 32) {
                title = title.substring(0, 32) + "...";
            }
            session.setTitle(title);
            saveSession(session);
        }
    }

    private synchronized void addSessionIdToIndex(String id) {
        List<String> ids = getSessionIdsIndex();
        if (!ids.contains(id)) {
            ids.add(id);
            saveSessionIdsIndex(ids);
        }
    }

    private synchronized List<String> getSessionIdsIndex() {
        String raw = prefs.getString(KEY_SESSION_IDS, "[]");
        List<String> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getString(i));
            }
        } catch (Exception ignored) {}
        return list;
    }

    private synchronized void saveSessionIdsIndex(List<String> ids) {
        JSONArray arr = new JSONArray(ids);
        prefs.edit().putString(KEY_SESSION_IDS, arr.toString()).apply();
    }
}