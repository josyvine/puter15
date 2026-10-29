package com.puter.unofficial;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure Java syntax colorizer tokenizing and coloring code blocks
 * (JSON, Python, JavaScript, Java, HTML, CSS, C++, Bash) for native code views.
 */
public class CodeSyntaxHighlighter {

    private static final int COLOR_KEYWORD = Color.parseColor("#569cd6"); // VS Code Blue
    private static final int COLOR_STRING = Color.parseColor("#ce9178");  // Orange/Rust
    private static final int COLOR_NUMBER = Color.parseColor("#b5cea8");  // Light Green
    private static final int COLOR_COMMENT = Color.parseColor("#6a9955"); // Muted Green
    private static final int COLOR_TAG = Color.parseColor("#4ec9b0");     // Cyan/Teal
    private static final int COLOR_BOOLEAN = Color.parseColor("#569cd6"); // Blue

    private static final Pattern PATTERN_KEYWORDS = Pattern.compile(
            "\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|function|var|let|const|def|self|async|await|None|True|False|null|true|false)\\b"
    );

    private static final Pattern PATTERN_STRINGS = Pattern.compile(
            "(\"[^\"]*\"|'[^']*'|`[^`]*`)"
    );

    private static final Pattern PATTERN_NUMBERS = Pattern.compile(
            "\\b(\\d+(\\.\\d+)?)\\b"
    );

    private static final Pattern PATTERN_COMMENTS = Pattern.compile(
            "(//.*|#.*|/\\*[\\s\\S]*?\\*/)"
    );

    private static final Pattern PATTERN_HTML_TAGS = Pattern.compile(
            "(<[^>]+>)"
    );

    public static CharSequence highlight(String code, String language) {
        if (code == null) return "";
        SpannableStringBuilder ssb = new SpannableStringBuilder(code);

        String lang = language != null ? language.toLowerCase() : "";

        // 1. Highlight Strings
        applyPattern(ssb, PATTERN_STRINGS, COLOR_STRING);

        // 2. Highlight Numbers
        applyPattern(ssb, PATTERN_NUMBERS, COLOR_NUMBER);

        // 3. Highlight Keywords (if not HTML/XML)
        if (!lang.contains("html") && !lang.contains("xml") && !lang.contains("svg")) {
            applyPattern(ssb, PATTERN_KEYWORDS, COLOR_KEYWORD);
        } else {
            applyPattern(ssb, PATTERN_HTML_TAGS, COLOR_TAG);
        }

        // 4. Highlight Comments (on top so comments override strings)
        applyPattern(ssb, PATTERN_COMMENTS, COLOR_COMMENT);

        return ssb;
    }

    private static void applyPattern(SpannableStringBuilder ssb, Pattern pattern, int color) {
        Matcher matcher = pattern.matcher(ssb);
        while (matcher.find()) {
            ssb.setSpan(
                    new ForegroundColorSpan(color),
                    matcher.start(),
                    matcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
    }
}
