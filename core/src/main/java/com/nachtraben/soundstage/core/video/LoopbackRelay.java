package com.nachtraben.soundstage.core.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LoopbackRelay implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger("Soundstage");

    public static final Set<Integer> RESERVED_PORTS = Set.of(25565, 25575, 25600, 25610);

    static final int MAX_CONNECTIONS = 4;

    private static final int MAX_HEAD_BYTES = 8192;
    private static final int HEAD_TIMEOUT_MILLIS = 10_000;
    private static final int COPY_BUFFER = 64 * 1024;

    private static final Pattern RANGE = Pattern.compile("(?i)\\s*bytes\\s*=\\s*(\\d+)\\s*-\\s*(\\d*)\\s*");

    @FunctionalInterface
    public interface Upstream {
        WebMediaFetch.Body open(long from) throws IOException;
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicInteger THREADS = new AtomicInteger();

    private final ServerSocket server;
    private final String path;
    private final URI url;
    private final Upstream upstream;
    private final String name;
    private final Set<Socket> open = ConcurrentHashMap.newKeySet();
    private final Thread acceptor;
    private volatile boolean closed;

    private LoopbackRelay(ServerSocket server, Upstream upstream, String name) {
        this.server = server;
        this.upstream = upstream;
        this.name = name;
        byte[] secret = new byte[16];
        RANDOM.nextBytes(secret);
        this.path = "/" + HexFormat.of().formatHex(secret);
        this.url = URI.create("http://127.0.0.1:" + server.getLocalPort() + path);
        this.acceptor = daemon("Soundstage-WebRelay-accept", this::acceptLoop);
    }

    public static LoopbackRelay open(Upstream upstream, String name) throws IOException {
        ServerSocket server = bind();
        LoopbackRelay relay = new LoopbackRelay(server, upstream, name);
        relay.acceptor.start();
        return relay;
    }

    private static ServerSocket bind() throws IOException {
        InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        for (int attempt = 0; attempt < 8; attempt++) {
            ServerSocket server = new ServerSocket();
            try {
                server.bind(new InetSocketAddress(loopback, 0), MAX_CONNECTIONS);
            } catch (IOException e) {
                server.close();
                throw e;
            }
            if (!RESERVED_PORTS.contains(server.getLocalPort())) return server;
            server.close();
        }
        throw new IOException("no loopback port outside the reserved ones");
    }

    public URI url() {
        return url;
    }

    public static boolean isRelayUrl(URI uri) {
        return uri != null && "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost())
                && uri.getPort() > 0 && !RESERVED_PORTS.contains(uri.getPort()) && uri.getRawQuery() == null
                && uri.getRawUserInfo() == null && uri.getRawPath() != null && uri.getRawPath().length() > 1;
    }

    @Override
    public void close() {
        closed = true;
        try {
            server.close();
        } catch (IOException ignored) {
        }
        for (Socket socket : open) closeQuietly(socket);
    }

    private void acceptLoop() {
        while (!closed) {
            Socket socket;
            try {
                socket = server.accept();
            } catch (IOException e) {
                if (!closed) LOGGER.debug("Soundstage: the web relay for {} stopped accepting: {}", name, e.toString());
                return;
            }
            if (closed || open.size() >= MAX_CONNECTIONS) {
                closeQuietly(socket);
                continue;
            }
            open.add(socket);
            Thread handler = daemon("Soundstage-WebRelay-" + THREADS.incrementAndGet(), () -> {
                try {
                    serve(socket);
                } finally {
                    open.remove(socket);
                    closeQuietly(socket);
                }
            });
            try {
                handler.start();
            } catch (RuntimeException | Error e) {
                open.remove(socket);
                closeQuietly(socket);
                throw e;
            }
        }
    }

    private void serve(Socket socket) {
        try {
            socket.setSoTimeout(HEAD_TIMEOUT_MILLIS);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            Request request = Request.read(in);
            if (request == null) {
                respond(out, 400, "Bad Request", null);
                return;
            }
            if (!request.path().equals(path)) {
                respond(out, 404, "Not Found", null);
                return;
            }
            boolean head = request.method().equals("HEAD");
            if (!head && !request.method().equals("GET")) {
                respond(out, 405, "Method Not Allowed", "Allow: GET, HEAD\r\n");
                return;
            }
            socket.setSoTimeout(0);
            relay(request, head, out);
        } catch (IOException e) {
            if (!closed) LOGGER.debug("Soundstage: the web relay for {} ended a request: {}", name, e.toString());
        }
    }

    private void relay(Request request, boolean head, OutputStream out) throws IOException {
        long from = request.from();
        long last = request.to();
        WebMediaFetch.Body body;
        try {
            body = upstream.open(from);
        } catch (IOException e) {
            LOGGER.debug("Soundstage: the web relay for {} could not fetch from byte {}: {}", name, from,
                    e.getMessage());
            respond(out, 502, "Bad Gateway", null);
            return;
        }
        try (body) {
            InputStream stream = body.stream();
            long start = body.start();
            if (!body.ranged() && from > 0) {
                stream.skipNBytes(from);
                start = from;
            }
            long total = body.total();
            if (total >= 0 && from >= total && from > 0) {
                respond(out, 416, "Range Not Satisfiable", "Content-Range: bytes */" + total + "\r\n");
                return;
            }
            long available = body.length() < 0 ? -1 : body.length() - (start - body.start());
            long send = available;
            if (last >= 0) {
                long wanted = last - start + 1;
                send = send < 0 ? wanted : Math.min(send, wanted);
            }
            StringBuilder headers = new StringBuilder();
            headers.append("Content-Type: application/octet-stream\r\n");
            if (send >= 0) headers.append("Content-Length: ").append(send).append("\r\n");
            int status;
            String reason;
            if (request.ranged() && body.ranged() || from > 0) {
                status = 206;
                reason = "Partial Content";
                String end = send >= 0 ? String.valueOf(start + send - 1)
                        : (total >= 0 ? String.valueOf(total - 1) : "");
                headers.append("Content-Range: bytes ").append(start).append('-').append(end).append('/')
                        .append(total >= 0 ? String.valueOf(total) : "*").append("\r\n");
            } else {
                status = 200;
                reason = "OK";
            }
            if (body.ranged()) headers.append("Accept-Ranges: bytes\r\n");
            respond(out, status, reason, headers.toString());
            if (head) return;
            copy(stream, out, send);
            out.flush();
        }
    }

    private static void copy(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER];
        long left = limit;
        while (left != 0) {
            int want = left < 0 ? buffer.length : (int) Math.min(buffer.length, left);
            int n = in.read(buffer, 0, want);
            if (n < 0) return;
            out.write(buffer, 0, n);
            if (left > 0) left -= n;
        }
    }

    private static void respond(OutputStream out, int status, String reason, String headers) throws IOException {
        String head = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + (headers == null ? "Content-Length: 0\r\n" : headers)
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    record Request(String method, String path, long from, long to, boolean ranged) {

        static Request read(InputStream in) throws IOException {
            String head = readHead(in);
            if (head == null) return null;
            String[] lines = head.split("\r?\n");
            String[] parts = lines[0].split(" ");
            if (parts.length != 3 || !parts[2].startsWith("HTTP/1.")) return null;
            String target = parts[1];
            int query = target.indexOf('?');
            String path = query < 0 ? target : target.substring(0, query);
            long from = 0;
            long to = -1;
            boolean ranged = false;
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon <= 0) continue;
                if (!lines[i].substring(0, colon).strip().toLowerCase(Locale.ROOT).equals("range")) continue;
                Matcher matcher = RANGE.matcher(lines[i].substring(colon + 1));
                if (matcher.matches()) {
                    try {
                        from = Long.parseLong(matcher.group(1));
                        to = matcher.group(2).isEmpty() ? -1 : Long.parseLong(matcher.group(2));
                        ranged = to < 0 || to >= from;
                        if (!ranged) {
                            from = 0;
                            to = -1;
                        }
                    } catch (NumberFormatException e) {
                        from = 0;
                        to = -1;
                    }
                }
            }
            return new Request(parts[0], path, from, to, ranged);
        }

        private static String readHead(InputStream in) throws IOException {
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            int matched = 0;
            while (head.size() < MAX_HEAD_BYTES) {
                int b = in.read();
                if (b < 0) return null;
                head.write(b);
                if (b == '\n') {
                    matched++;
                    if (matched == 2) return head.toString(StandardCharsets.US_ASCII);
                } else if (b != '\r') {
                    matched = 0;
                }
            }
            return null;
        }
    }

    private static Thread daemon(String name, Runnable body) {
        Thread thread = new Thread(body, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }
}
