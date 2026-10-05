package com.nachtraben.soundstage.core.video;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

public final class MediaNames {

    public static final int MAX_LENGTH = 200;

    private MediaNames() {}

    public static boolean isAcceptable(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_LENGTH) return false;
        if (name.charAt(0) == '.') return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '/' || c == '\\' || Character.isISOControl(c)) return false;
        }
        return true;
    }

    public static Optional<Path> resolve(Path root, String name) {
        if (!isAcceptable(name)) return Optional.empty();
        Path folder = root.toAbsolutePath().normalize();
        Path file;
        try {
            file = folder.resolve(name).normalize();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
        if (!folder.equals(file.getParent()) || !Files.isRegularFile(file)) return Optional.empty();
        return Optional.of(file);
    }
}
