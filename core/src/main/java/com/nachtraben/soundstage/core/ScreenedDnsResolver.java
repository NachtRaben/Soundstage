package com.nachtraben.soundstage.core;

import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.conn.DnsResolver;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.impl.conn.SystemDefaultDnsResolver;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class ScreenedDnsResolver implements DnsResolver {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Soundstage");

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static final int MAX_WARNED = 256;

    private final DnsResolver underlying;
    private final Predicate<InetAddress> permitted;
    private final String platform;

    public ScreenedDnsResolver(DnsResolver underlying, Predicate<InetAddress> permitted) {
        this(underlying, permitted, null);
    }

    private ScreenedDnsResolver(DnsResolver underlying, Predicate<InetAddress> permitted, String platform) {
        this.underlying = underlying;
        this.permitted = permitted;
        this.platform = platform;
    }

    public static ScreenedDnsResolver publicOnly() {
        return new ScreenedDnsResolver(SystemDefaultDnsResolver.INSTANCE, UrlPolicy::isPubliclyRoutable);
    }

    public static ScreenedDnsResolver platform(String name) {
        return platform(name, SystemDefaultDnsResolver.INSTANCE);
    }

    static ScreenedDnsResolver platform(String name, DnsResolver underlying) {
        return new ScreenedDnsResolver(underlying, UrlPolicy::isPubliclyRoutable, name);
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] resolved = underlying.resolve(host);
        List<InetAddress> kept = new ArrayList<>();
        for (InetAddress address : resolved) {
            if (permitted.test(address)) kept.add(address);
        }
        if (!kept.isEmpty()) return kept.toArray(new InetAddress[0]);
        if (platform == null) {
            throw new PrivateAddressRefusedException(
                    host + " resolves only to non-public addresses, which Soundstage fetches from only for an operator, "
                            + "and only with allowPrivateNetworkHosts=true.", false);
        }
        String addresses = Arrays.toString(Arrays.stream(resolved).map(InetAddress::getHostAddress).toArray());
        if (WARNED.size() < MAX_WARNED && WARNED.add(host)) {
            LOGGER.warn("Soundstage: the {} source refused {}, which resolved only to non-public addresses {}. A "
                    + "platform never needs a private address, so the usual cause is this server's DNS: a fake-IP "
                    + "DNS proxy (Clash, mihomo, sing-box, Surge in TUN mode) answers every name from 198.18.0.0/15, "
                    + "and then every platform load fails. Give the server a real resolver, or exclude it from the "
                    + "proxy's fake-IP mode.", platform, host, addresses);
        }
        throw new PrivateAddressRefusedException(
                host + " resolves only to non-public addresses " + addresses + ", which the " + platform
                        + " source never fetches from.", true);
    }
}
