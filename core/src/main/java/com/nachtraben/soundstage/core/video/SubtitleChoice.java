package com.nachtraben.soundstage.core.video;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;

public final class SubtitleChoice {

    public static final String AUTO = "auto";
    public static final String OFF = "off";

    public record Option(String key, String label) {}

    private SubtitleChoice() {}

    public static Optional<String> resolve(String choice, List<Option> options, String autoKey) {
        if (OFF.equals(choice)) return Optional.empty();
        if (choice != null && !AUTO.equals(choice)) {
            for (Option option : options) {
                if (option.key().equals(choice)) return Optional.of(choice);
            }
        }
        return Optional.ofNullable(autoKey);
    }

    public static String next(String current, List<Option> options) {
        if (current == null || AUTO.equals(current)) return OFF;
        if (OFF.equals(current)) return options.isEmpty() ? AUTO : options.get(0).key();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).key().equals(current)) {
                return i + 1 < options.size() ? options.get(i + 1).key() : AUTO;
            }
        }
        return AUTO;
    }

    public static String describe(String choice, List<Option> options, String autoKey) {
        if (OFF.equals(choice)) return "Off";
        if (choice != null && !AUTO.equals(choice)) {
            for (Option option : options) {
                if (option.key().equals(choice)) return option.label();
            }
        }
        String picked = autoKey == null ? "none" : label(autoKey, options);
        return "Auto (" + picked + ")";
    }

    private static String label(String key, List<Option> options) {
        for (Option option : options) {
            if (option.key().equals(key)) return option.label();
        }
        return key;
    }

    public static String mediaLabel(String language, String title, boolean forced) {
        String name = languageName(language);
        StringBuilder label = new StringBuilder(name == null ? "Unknown language" : name);
        if (title != null && !title.isBlank() && (name == null || !title.strip().equalsIgnoreCase(name))) {
            label.append(" (").append(title.strip()).append(')');
        }
        if (forced) label.append(", forced");
        return label.toString();
    }

    private static final Map<String, String> ISO3 = new HashMap<>();

    static {
        for (String two : Locale.getISOLanguages()) {
            @SuppressWarnings("deprecation")
            Locale locale = new Locale(two);
            try {
                ISO3.put(locale.getISO3Language(), locale.getDisplayLanguage(Locale.ENGLISH));
            } catch (MissingResourceException ignored) {
            }
        }
        ISO3.putIfAbsent("ger", "German");
        ISO3.putIfAbsent("fre", "French");
        ISO3.putIfAbsent("dut", "Dutch");
        ISO3.putIfAbsent("chi", "Chinese");
        ISO3.putIfAbsent("cze", "Czech");
        ISO3.putIfAbsent("gre", "Greek");
        ISO3.putIfAbsent("per", "Persian");
        ISO3.putIfAbsent("rum", "Romanian");
        ISO3.putIfAbsent("slo", "Slovak");
        ISO3.putIfAbsent("ice", "Icelandic");
        ISO3.putIfAbsent("arm", "Armenian");
        ISO3.putIfAbsent("baq", "Basque");
        ISO3.putIfAbsent("geo", "Georgian");
        ISO3.putIfAbsent("mac", "Macedonian");
        ISO3.putIfAbsent("may", "Malay");
        ISO3.putIfAbsent("alb", "Albanian");
        ISO3.putIfAbsent("bur", "Burmese");
        ISO3.putIfAbsent("wel", "Welsh");
        ISO3.putIfAbsent("tib", "Tibetan");
    }

    static String languageName(String language) {
        if (language == null || language.isBlank()) return null;
        String tag = language.strip();
        String base = tag.split("[-_]")[0].toLowerCase(Locale.ROOT);
        if (base.equals("und")) return null;
        if (base.length() == 3) return ISO3.get(base);
        if (base.length() == 2) {
            @SuppressWarnings("deprecation") String name = new Locale(base).getDisplayLanguage(Locale.ENGLISH);
            return name.isEmpty() || name.equalsIgnoreCase(base) ? null : name;
        }
        return null;
    }

    public static final class Memory {
        private static final int CAPACITY = 256;
        private final Map<String, String> choices = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > CAPACITY;
            }
        };

        public synchronized String get(String identifier) {
            if (identifier == null) return AUTO;
            return choices.getOrDefault(identifier, AUTO);
        }

        public synchronized void put(String identifier, String choice) {
            if (identifier == null) return;
            if (choice == null || AUTO.equals(choice)) choices.remove(identifier);
            else choices.put(identifier, choice);
        }
    }
}
