package com.puter.unofficial;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;

/**
 * Pure Java Markdown rendering engine transforming markdown into native Android
 * SpannableStringBuilder using Markwon and custom code block extractor.
 */
public class NativeMarkdownParser {
    private static Markwon markwon;

    public static class ParsedMarkdown {
        public String mainText;
        public List<ExtractedCodeBlock> codeBlocks = new ArrayList<>();
    }

    public static class ExtractedCodeBlock {
        public String language;
        public String code;

        public ExtractedCodeBlock(String language, String code) {
            this.language = language;
            this.code = code;
        }
    }

    public static synchronized void init(Context context) {
        if (markwon == null) {
            markwon = Markwon.builder(context.getApplicationContext())
                    .usePlugin(TablePlugin.create(context.getApplicationContext()))
                    .usePlugin(StrikethroughPlugin.create())
                    .build();
        }
    }

    public static void renderToTextView(TextView textView, String markdownText) {
        if (textView == null) return;
        if (markdownText == null || markdownText.isEmpty()) {
            textView.setText("");
            return;
        }
        if (markwon != null) {
            markwon.setMarkdown(textView, markdownText);
        } else {
            init(textView.getContext());
            markwon.setMarkdown(textView, markdownText);
        }
    }

    /**
     * Splits raw markdown into body text and separate code blocks so
     * that code blocks can be given rich native containers with preview and copy buttons.
     */
    public static ParsedMarkdown extractCodeBlocks(String rawText) {
        ParsedMarkdown result = new ParsedMarkdown();
        if (rawText == null || rawText.isEmpty()) {
            result.mainText = "";
            return result;
        }

        Pattern codeBlockPattern = Pattern.compile("```([a-zA-Z0-9_-]*)\\s*\\n([\\s\\S]*?)```");
        Matcher matcher = codeBlockPattern.matcher(rawText);
        StringBuffer cleanText = new StringBuffer();

        while (matcher.find()) {
            String lang = matcher.group(1);
            if (lang == null || lang.trim().isEmpty()) lang = "code";
            String code = matcher.group(2);

            result.codeBlocks.add(new ExtractedCodeBlock(lang.trim(), code != null ? code.trim() : ""));
            matcher.appendReplacement(cleanText, "");
        }
        matcher.appendTail(cleanText);

        result.mainText = cleanText.toString().trim();
        return result;
    }
}
