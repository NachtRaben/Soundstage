package com.nachtraben.soundstage.core;

import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.HttpHost;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.client.utils.URIUtils;

import java.net.URI;
import java.util.Locale;

public final class PrivateNetworkAccess {

    public static final String REFUSED_BY_SERVER =
            "That address is on a private network, which this server does not play from.";

    public static final String REFUSED_NOT_OPERATOR =
            "Only an operator can play from a private network address on this server.";

    public static final String REFUSED_PLATFORM =
            "The server could not reach that service: its address resolved to a private network. The server log has the details.";

    public static final String REFUSED_HOST_FORM =
            "That URL's host is not written in a form this server accepts; write the full address or name.";

    private PrivateNetworkAccess() {}

    public static boolean permits(boolean allowPrivateNetworkHosts, boolean operator) {
        return allowPrivateNetworkHosts && operator;
    }

    public static AudioReference reference(String identifier, boolean allowPrivateNetworkHosts, boolean operator) {
        return permits(allowPrivateNetworkHosts, operator)
                ? new AdmittedReference(identifier, null, null)
                : new AudioReference(identifier, null);
    }

    public static boolean unreadableHost(String identifier) {
        if (identifier == null) return false;
        String trimmed = identifier.strip();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        boolean icy = lower.startsWith("icy://");
        if (!lower.startsWith("http://") && !lower.startsWith("https://") && !icy) return false;
        URI uri;
        try {
            uri = new URI(icy ? "http://" + trimmed.substring(6) : trimmed);
        } catch (java.net.URISyntaxException e) {
            return true;
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) return true;
        HttpHost apache = URIUtils.extractHost(uri);
        return apache == null || !unbracketed(apache.getHostName()).equalsIgnoreCase(unbracketed(host));
    }

    private static String unbracketed(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    public static String messageFor(Throwable failure, boolean allowPrivateNetworkHosts) {
        for (Throwable at = failure; at != null; at = at.getCause() == at ? null : at.getCause()) {
            if (at instanceof PrivateAddressRefusedException refused) {
                if (refused.platform()) return REFUSED_PLATFORM;
                return allowPrivateNetworkHosts ? REFUSED_NOT_OPERATOR : REFUSED_BY_SERVER;
            }
        }
        return null;
    }
}
