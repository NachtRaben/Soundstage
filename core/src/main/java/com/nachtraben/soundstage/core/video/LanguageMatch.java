package com.nachtraben.soundstage.core.video;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class LanguageMatch {

    private static final Map<String, String> THREE_TO_TWO = new HashMap<>();

    static {
        for (String two : Locale.getISOLanguages()) {
            @SuppressWarnings("deprecation")
            Locale locale = new Locale(two);
            try {
                THREE_TO_TWO.put(locale.getISO3Language(), two);
            } catch (java.util.MissingResourceException ignored) {
            }
        }
        String[][] bibliographic = {
                {"ger", "de"}, {"fre", "fr"}, {"dut", "nl"}, {"chi", "zh"}, {"cze", "cs"}, {"gre", "el"},
                {"per", "fa"}, {"rum", "ro"}, {"slo", "sk"}, {"ice", "is"}, {"arm", "hy"}, {"baq", "eu"},
                {"geo", "ka"}, {"mac", "mk"}, {"may", "ms"}, {"alb", "sq"}, {"bur", "my"}, {"wel", "cy"},
                {"tib", "bo"}, {"mao", "mi"}};
        for (String[] pair : bibliographic) THREE_TO_TWO.putIfAbsent(pair[0], pair[1]);
    }

    private LanguageMatch() {}

    public static String language(String tag) {
        if (tag == null || tag.isBlank()) return null;
        String base = tag.strip().split("[-_]")[0].toLowerCase(Locale.ROOT);
        if (base.isEmpty() || base.equals("und") || base.equals("mul") || base.equals("zxx")) return null;
        if (base.length() == 3) return THREE_TO_TWO.getOrDefault(base, base);
        return base;
    }

    public static String region(String tag) {
        if (tag == null) return null;
        String[] parts = tag.strip().split("[-_]");
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.length() == 2 || (part.length() == 3 && part.chars().allMatch(Character::isDigit))) {
                return part.toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    public static boolean sameLanguage(String a, String b) {
        String la = language(a);
        return la != null && la.equals(language(b));
    }

    public static <T> Optional<T> best(String playerLanguage, List<T> candidates, Function<T, String> tagOf) {
        if (language(playerLanguage) == null) return Optional.empty();
        String region = region(playerLanguage);
        T first = null;
        for (T candidate : candidates) {
            String tag = tagOf.apply(candidate);
            if (!sameLanguage(playerLanguage, tag)) continue;
            if (region != null && region.equals(region(tag))) return Optional.of(candidate);
            if (first == null) first = candidate;
        }
        return Optional.ofNullable(first);
    }
}
