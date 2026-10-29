package com.puter.unofficial.models;

public class GroundingSource {
    private String url;
    private String title;
    private String snippet;
    private String searchQuery;

    public GroundingSource(String url, String title, String snippet, String searchQuery) {
        this.url = url;
        this.title = title;
        this.snippet = snippet;
        this.searchQuery = searchQuery;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }
}
