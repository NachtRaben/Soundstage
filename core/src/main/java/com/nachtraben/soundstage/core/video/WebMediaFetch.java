package com.nachtraben.soundstage.core.video;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.Header;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.HttpEntity;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.client.methods.CloseableHttpResponse;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.client.methods.HttpGet;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WebMediaFetch {

    public static final int MAX_REDIRECTS = 5;

    private static final Pattern CONTENT_RANGE = Pattern.compile("(?i)\\s*bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)\\s*");

    private WebMediaFetch() {}

    public interface Body extends AutoCloseable {
        long start();

        long length();

        long total();

        boolean ranged();

        InputStream stream();

        @Override
        void close();
    }

    public static boolean isFetchable(URI url) {
        if (url == null || !url.isAbsolute() || url.isOpaque()) return false;
        String scheme = url.getScheme().toLowerCase(Locale.ROOT);
        return (scheme.equals("http") || scheme.equals("https")) && url.getHost() != null
                && !url.getHost().isEmpty() && url.getRawUserInfo() == null;
    }

    public static Body open(Supplier<HttpInterface> client, URI url, long from) throws IOException {
        HttpInterface http = client.get();
        try {
            Body body = open(http, url, from);
            return new Owning(body, http);
        } catch (IOException | RuntimeException e) {
            closeQuietly(http);
            throw e;
        }
    }

    private static Body open(HttpInterface http, URI url, long from) throws IOException {
        URI at = url;
        for (int hop = 0; ; hop++) {
            if (!isFetchable(at)) {
                throw new IOException("refused to fetch a URL that is not http or https"
                        + (hop == 0 ? "" : " (redirect " + hop + ")"));
            }
            HttpGet get = new HttpGet(at);
            get.setHeader("Range", "bytes=" + Math.max(0, from) + "-");
            get.setHeader("Accept-Encoding", "identity");
            CloseableHttpResponse response = http.execute(get);
            int status = response.getStatusLine().getStatusCode();
            if (status >= 300 && status < 400 && status != 304) {
                Header location = response.getFirstHeader("Location");
                closeQuietly(response);
                if (location == null) throw new IOException("a redirect with nowhere to go");
                if (hop >= MAX_REDIRECTS) throw new IOException("more than " + MAX_REDIRECTS + " redirects");
                try {
                    at = at.resolve(location.getValue().strip());
                } catch (IllegalArgumentException e) {
                    throw new IOException("a redirect to something that is not a URL", e);
                }
                continue;
            }
            if (status != 200 && status != 206) {
                closeQuietly(response);
                throw new IOException("the server answered " + status);
            }
            return body(response, status, from);
        }
    }

    private static Body body(CloseableHttpResponse response, int status, long from) throws IOException {
        HttpEntity entity = response.getEntity();
        if (entity == null) {
            closeQuietly(response);
            throw new IOException("the server answered with no body");
        }
        long length = entity.getContentLength();
        long start = 0;
        long total = status == 200 ? length : -1;
        boolean ranged = false;
        if (status == 206) {
            Header range = response.getFirstHeader("Content-Range");
            Matcher matcher = range == null ? null : CONTENT_RANGE.matcher(range.getValue());
            if (matcher == null || !matcher.matches()) {
                closeQuietly(response);
                throw new IOException("a partial answer that does not say which part");
            }
            start = Long.parseLong(matcher.group(1));
            long end = Long.parseLong(matcher.group(2));
            total = matcher.group(3).equals("*") ? -1 : Long.parseLong(matcher.group(3));
            if (length < 0) length = end - start + 1;
            ranged = true;
        }
        if (ranged && start != Math.max(0, from)) {
            closeQuietly(response);
            throw new IOException("the server answered a different range than was asked for");
        }
        InputStream stream = entity.getContent();
        long bodyStart = start;
        long bodyLength = length;
        long bodyTotal = total;
        boolean bodyRanged = ranged;
        return new Body() {
            @Override
            public long start() {
                return bodyStart;
            }

            @Override
            public long length() {
                return bodyLength;
            }

            @Override
            public long total() {
                return bodyTotal;
            }

            @Override
            public boolean ranged() {
                return bodyRanged;
            }

            @Override
            public InputStream stream() {
                return stream;
            }

            @Override
            public void close() {
                closeQuietly(response);
            }
        };
    }

    private record Owning(Body body, HttpInterface http) implements Body {
        @Override
        public long start() {
            return body.start();
        }

        @Override
        public long length() {
            return body.length();
        }

        @Override
        public long total() {
            return body.total();
        }

        @Override
        public boolean ranged() {
            return body.ranged();
        }

        @Override
        public InputStream stream() {
            return body.stream();
        }

        @Override
        public void close() {
            body.close();
            closeQuietly(http);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
