package uk.ac.dundee.ga.mms.util;

import java.util.regex.Pattern;

/** Trims input and strips HTML tags on save (Section 8.2); the frontend escapes on display. */
public final class TextSanitizer {

    private static final Pattern SCRIPT = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern TAGS = Pattern.compile("(?s)<[^>]*>");

    private TextSanitizer() {
    }

    public static String clean(String in) {
        if (in == null) {
            return null;
        }
        String s = SCRIPT.matcher(in).replaceAll("");
        s = TAGS.matcher(s).replaceAll("");
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    public static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
