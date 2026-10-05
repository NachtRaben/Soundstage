package com.nachtraben.soundstage.core;

import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedConnectionOperator;
import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpClientBuilder;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.HttpHost;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.config.SocketConfig;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.conn.DnsResolver;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.conn.HttpClientConnectionOperator;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.conn.ManagedHttpClientConnection;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.impl.client.HttpClientBuilder;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public final class ScreenedConnections {

    public static final long CONNECTION_TTL_SECONDS = 30;

    private ScreenedConnections() {}

    public static void install(HttpClientBuilder builder, DnsResolver screen) {
        if (builder instanceof ExtendedHttpClientBuilder extended) {
            extended.setConnectionManagerFactory((operator, connections) -> {
                PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager(
                        new Screening(operator, screen), connections, CONNECTION_TTL_SECONDS, TimeUnit.SECONDS);
                manager.setMaxTotal(3000);
                manager.setDefaultMaxPerRoute(1500);
                return manager;
            });
        }
        builder.setDnsResolver(screen);
    }

    private record Screening(HttpClientConnectionOperator operator, DnsResolver screen)
            implements HttpClientConnectionOperator {

        @Override
        public void connect(ManagedHttpClientConnection connection, HttpHost host, InetSocketAddress localAddress,
                            int connectTimeout, SocketConfig socketConfig, HttpContext context) throws IOException {
            InetAddress[] addresses;
            if (host.getAddress() != null) {
                addresses = screen.resolve(host.getAddress().getHostAddress());
                if (addresses.length == 0 || !addresses[0].equals(host.getAddress())) {
                    throw new PrivateAddressRefusedException(host.getAddress().getHostAddress() + " was refused", false);
                }
            } else {
                addresses = screen.resolve(host.getHostName());
            }
            ExtendedConnectionOperator.setResolvedAddresses(context, host, addresses);
            operator.connect(connection, host, localAddress, connectTimeout, socketConfig, context);
        }

        @Override
        public void upgrade(ManagedHttpClientConnection connection, HttpHost host, HttpContext context)
                throws IOException {
            operator.upgrade(connection, host, context);
        }
    }
}
