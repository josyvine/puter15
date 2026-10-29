package com.puter.unofficial.models;

import android.graphics.Bitmap;
import android.net.Uri;

public class StagedFile {
    private Uri uri;
    private String filename;
    private String mimeType;
    private long sizeBytes;
    private String base64DataUri;
    private Bitmap thumbnailBitmap;

    public StagedFile(Uri uri, String filename, String mimeType, long sizeBytes, String base64DataUri, Bitmap thumbnailBitmap) {
        this.uri = uri;
        this.filename = filename;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.base64DataUri = base64DataUri;
        this.thumbnailBitmap = thumbnailBitmap;
    }

    public Uri getUri() {
        return uri;
    }

    public void setUri(Uri uri) {
        this.uri = uri;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getBase64DataUri() {
        return base64DataUri;
    }

    public void setBase64DataUri(String base64DataUri) {
        this.base64DataUri = base64DataUri;
    }

    public Bitmap getThumbnailBitmap() {
        return thumbnailBitmap;
    }

    public void setThumbnailBitmap(Bitmap thumbnailBitmap) {
        this.thumbnailBitmap = thumbnailBitmap;
    }

    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }
}
