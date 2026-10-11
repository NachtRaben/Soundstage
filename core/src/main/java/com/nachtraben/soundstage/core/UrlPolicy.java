package com.nachtraben.soundstage.core;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class UrlPolicy {

    private final List<Pattern> allow;
    private final List<Pattern> deny;

    private UrlPolicy(List<Pattern> allow, List<Pattern> deny) {
        this.allow = allow;
        this.deny = deny;
    }

    public static UrlPolicy of(List<? extends String> allowPatterns, List<? extends String> denyPatterns) {
        return new UrlPolicy(compile(allowPatterns), compile(denyPatterns));
    }

    private static List<Pattern> compile(List<? extends String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<Pattern> compiled = new ArrayList<>(raw.size());
        for (String s : raw) {
            try {
                compiled.add(Pattern.compile(s, Pattern.CASE_INSENSITIVE));
            } catch (PatternSyntaxException ignored) {
            }
        }
        return List.copyOf(compiled);
    }

    public boolean allows(String url) {
        if (url == null || url.isBlank()) return false;
        if (!allow.isEmpty()) {
            for (Pattern p : allow) {
                if (p.matcher(url).matches()) return true;
            }
            return false;
        }
        for (Pattern p : deny) {
            if (p.matcher(url).matches()) return false;
        }
        return true;
    }

    public static boolean isValidPattern(Object value) {
        if (!(value instanceof String s) || s.isBlank()) return false;
        try {
            Pattern.compile(s, Pattern.CASE_INSENSITIVE);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private static boolean embedsIpv4(byte[] bytes) {
        boolean nat64 = bytes[0] == 0x00 && bytes[1] == 0x64 && (bytes[2] & 0xFF) == 0xFF && (bytes[3] & 0xFF) == 0x9B;
        if (nat64 && isZero(bytes, 4, 12)) return true;
        if (nat64 && bytes[4] == 0x00 && bytes[5] == 0x01) return true;
        if (isZero(bytes, 0, 12)) return true;
        if (isZero(bytes, 0, 10) && (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF) {
            return true;
        }
        return isZero(bytes, 0, 8) && (bytes[8] & 0xFF) == 0xFF && (bytes[9] & 0xFF) == 0xFF
                && isZero(bytes, 10, 12);
    }

    private static boolean isZero(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) return false;
        }
        return true;
    }

    public static boolean isPubliclyRoutable(InetAddress address) {
        if (address == null) return false;
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            int third = bytes[2] & 0xFF;
            if (first == 100 && second >= 64 && second <= 127) return false;
            if (first == 0) return false;
            if (first >= 240) return false;
            if (first == 192 && second == 0 && third == 0) return false;
            if (first == 198 && (second == 18 || second == 19)) return false;
            return !(first == 169 && second == 254);
        }
        if (bytes.length == 16 && embedsIpv4(bytes)) {
            try {
                return isPubliclyRoutable(InetAddress.getByAddress(Arrays.copyOfRange(bytes, 12, 16)));
            } catch (UnknownHostException impossible) {
                return false;
            }
        }
        return !(bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC);
    }
}
