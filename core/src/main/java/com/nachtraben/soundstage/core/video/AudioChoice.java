package com.nachtraben.soundstage.core.video;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AudioChoice {

    public static final String DEFAULT = "default";

    public static final String LANGUAGE = "lang:";

    public static final String UNLABELLED = "Unlabelled";

    public enum Kind {
        MAIN,
        COMMENTARY,
        DESCRIPTION
    }

    public record Option(String key, String label) {}

    public record Entry(String key, String label, String language, boolean perTrack, boolean original) {
        public String choiceKey() {
            return perTrack ? key : LANGUAGE + language;
        }
    }

    public record Offer(List<Option> options, String selected, String defaultLabel, String missingLanguage) {
        public Offer {
            options = List.copyOf(options);
            selected = selected == null ? DEFAULT : selected;
        }

        public boolean shown() {
            return options.size() > 1 || selected.startsWith(LANGUAGE);
        }
    }

    private AudioChoice() {}

    public static String resolve(List<Entry> entries, String language, String trackChoice) {
        if (entries == null || entries.isEmpty()) return null;
        if (trackChoice != null) {
            for (Entry entry : entries) {
                if (entry.perTrack() && entry.key().equals(trackChoice)) return entry.key();
            }
        }
        Entry spoken = spoken(entries, language);
        return spoken != null ? spoken.key() : original(entries).key();
    }

    public static Entry original(List<Entry> entries) {
        for (Entry entry : entries) {
            if (entry.original()) return entry;
        }
        for (Entry entry : entries) {
            if (!entry.perTrack()) return entry;
        }
        return entries.get(0);
    }

    static Entry spoken(List<Entry> entries, String language) {
        if (language == null) return null;
        String wanted = languageTag(language);
        if (wanted == null) return null;
        for (Entry entry : entries) {
            if (!entry.perTrack() && wanted.equals(entry.language())) return entry;
        }
        for (Entry entry : entries) {
            if (!entry.perTrack() && LanguageMatch.sameLanguage(entry.language(), wanted)) return entry;
        }
        return null;
    }

    public static Offer offer(List<Entry> entries, String language, String trackChoice) {
        List<Entry> known = entries == null ? List.of() : entries;
        Map<String, Option> options = new LinkedHashMap<>();
        for (Entry entry : known) options.putIfAbsent(entry.choiceKey(), new Option(entry.choiceKey(), entry.label()));
        String defaultLabel = known.isEmpty() ? null : original(known).label();
        if (trackChoice != null) {
            for (Entry entry : known) {
                if (entry.perTrack() && entry.key().equals(trackChoice)) {
                    return new Offer(new ArrayList<>(options.values()), trackChoice, defaultLabel, null);
                }
            }
        }
        String wanted = languageTag(language);
        if (wanted == null) return new Offer(new ArrayList<>(options.values()), DEFAULT, defaultLabel, null);
        Entry spoken = spoken(known, wanted);
        if (spoken != null) return new Offer(new ArrayList<>(options.values()), spoken.choiceKey(), defaultLabel, null);
        return new Offer(new ArrayList<>(options.values()), LANGUAGE + wanted, defaultLabel, languageLabel(wanted));
    }

    public static boolean accepts(Offer offer, String key) {
        if (offer == null || key == null) return false;
        if (key.equals(DEFAULT) || key.equals(offer.selected())) return true;
        for (Option option : offer.options()) {
            if (option.key().equals(key)) return true;
        }
        return false;
    }

    public static String label(String key, List<Option> options) {
        for (Option option : options) {
            if (option.key().equals(key)) return option.label();
        }
        return key;
    }

    public static String labelOf(String key, List<Entry> entries) {
        for (Entry entry : entries) {
            if (entry.key().equals(key)) return entry.label();
        }
        return key;
    }

    public static String languageTag(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.strip().split("[-_]");
        String language = LanguageMatch.language(parts[0]);
        if (language == null) return null;
        StringBuilder tag = new StringBuilder(language);
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            boolean script = part.length() == 4 && part.chars().allMatch(Character::isLetter);
            boolean region = part.length() == 2 && part.chars().allMatch(Character::isLetter)
                    || part.length() == 3 && part.chars().allMatch(Character::isDigit);
            if (script || region) tag.append('-').append(part.toLowerCase(Locale.ROOT));
        }
        return tag.toString();
    }

    public static String languageLabel(String tag) {
        String name = SubtitleChoice.languageName(tag);
        if (name == null) return tag == null ? UNLABELLED : tag;
        String region = LanguageMatch.region(tag);
        return region == null ? name : name + " (" + region.toUpperCase(Locale.ROOT) + ")";
    }

    public static String mediaLabel(String language, String title, Kind kind) {
        String base;
        if (SubtitleChoice.languageName(language) != null) {
            base = languageLabel(language);
        } else if (LanguageMatch.language(language) != null) {
            base = language.strip();
        } else {
            base = UNLABELLED;
        }
        if (kind == Kind.COMMENTARY) base += ", commentary";
        if (kind == Kind.DESCRIPTION) base += ", audio description";
        if (kind != Kind.MAIN && title != null && !title.isBlank()) base += " (" + title.strip() + ")";
        return base;
    }
}
