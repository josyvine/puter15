package com.puter.unofficial;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.puter.unofficial.logging.LiveLogManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Memory-safe utility class to handle general file operations.
 * Converts documents, text files, and media attachments into Data URIs
 * with strict memory guards to prevent OutOfMemory crashes on large videos.
 */
public class FileUtils {

    private static final String TAG = "PuterFileUtils";
    private static final long MAX_SAFE_FILE_SIZE_BYTES = 25 * 1024 * 1024; // 25MB safety ceiling

    /**
     * Reads a file and encodes it to a Base64 Data URI with memory exhaustion guards.
     * Format: data:[mime/type];base64,[data]
     * 
     * @param context App context.
     * @param fileUri The Uri from the file picker.
     * @return Full Data URI string, or null if an error or OOM occurs.
     */
    public static String fileToDataUri(Context context, Uri fileUri) {
        if (context == null || fileUri == null) return null;

        // Check file size first before reading to protect the heap
        long fileSize = getFileSize(context, fileUri);
        if (fileSize > MAX_SAFE_FILE_SIZE_BYTES) {
            Log.w(TAG, "File size (" + fileSize + " bytes) exceeds maximum memory ceiling of 25MB.");
            LiveLogManager.getInstance().warn("FILE_UTILS", "File Too Large", "Skipped Base64 allocation for file: " + fileSize + " bytes");
            return null;
        }

        InputStream inputStream = null;
        try {
            ContentResolver contentResolver = context.getContentResolver();
            String mimeType = contentResolver.getType(fileUri);
            
            if (mimeType == null || mimeType.isEmpty()) {
                mimeType = getMimeType(context, fileUri);
            }

            inputStream = contentResolver.openInputStream(fileUri);
            if (inputStream == null) return null;

            // Pre-allocate buffer to prevent continuous array reallocation in heap
            int initialCapacity = (fileSize > 0 && fileSize <= MAX_SAFE_FILE_SIZE_BYTES) ? (int) fileSize : 16384;
            ByteArrayOutputStream output = new ByteArrayOutputStream(initialCapacity);
            byte[] buffer = new byte[16384];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }

            byte[] fileBytes = output.toByteArray();
            output.close();

            String base64Data = Base64.encodeToString(fileBytes, Base64.NO_WRAP);
            return "data:" + mimeType + ";base64," + base64Data;

        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "OutOfMemoryError caught while encoding file to Data URI: " + oom.getMessage());
            LiveLogManager.getInstance().error("FILE_UTILS", "OOM in fileToDataUri", "Heap exhausted while converting media. Allocation aborted safely.");
            System.gc();
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to convert file to Data URI: " + e.getMessage());
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing stream: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Resolves the exact file size in bytes using OpenableColumns or stream estimation.
     */
    public static long getFileSize(Context context, Uri uri) {
        if (context == null || uri == null) return 0;
        long size = 0;
        Cursor cursor = null;
        try {
            if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
                cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex);
                    }
                }
            } else if (ContentResolver.SCHEME_FILE.equals(uri.getScheme()) && uri.getPath() != null) {
                java.io.File file = new java.io.File(uri.getPath());
                size = file.length();
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return size;
    }

    /**
     * Helper to resolve MIME type from Uri or File Extension with complete video/audio support.
     */
    public static String getMimeType(Context context, Uri uri) {
        if (context == null || uri == null) return "application/octet-stream";
        String mimeType = null;
        try {
            if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
                ContentResolver cr = context.getContentResolver();
                mimeType = cr.getType(uri);
            } else {
                String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
                if (fileExtension != null) {
                    mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase(java.util.Locale.US));
                }
            }
        } catch (Exception ignored) {}

        if (mimeType == null) {
            String path = uri.toString().toLowerCase(java.util.Locale.US);
            if (path.endsWith(".mp4")) mimeType = "video/mp4";
            else if (path.endsWith(".mkv")) mimeType = "video/x-matroska";
            else if (path.endsWith(".webm")) mimeType = "video/webm";
            else if (path.endsWith(".mov")) mimeType = "video/quicktime";
            else if (path.endsWith(".3gp")) mimeType = "video/3gpp";
            else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) mimeType = "image/jpeg";
            else if (path.endsWith(".png")) mimeType = "image/png";
            else if (path.endsWith(".webp")) mimeType = "image/webp";
            else if (path.endsWith(".pdf")) mimeType = "application/pdf";
            else if (path.endsWith(".txt")) mimeType = "text/plain";
            else mimeType = "application/octet-stream";
        }
        return mimeType;
    }

    /**
     * Extracts the readable file name from a Uri.
     */
    public static String getFileName(Context context, Uri uri) {
        if (context == null || uri == null) return "attachment";
        String result = null;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null && uri.getPath() != null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result != null ? result : "attachment";
    }
}