package edu.stankin.cogoalgateway;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stands in for main or auth behind the gateway: answers 200 with its name and remembers every request it got.
 */
class StubService implements AutoCloseable {

    record Received(String method, String path, Headers headers, long bodyLength) {
    }

    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();

    StubService(String name, Map<String, List<String>> extraResponseHeaders) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            long bodyLength;
            try (InputStream body = exchange.getRequestBody()) {
                bodyLength = body.transferTo(OutputStream.nullOutputStream());
            }
            received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
                    exchange.getRequestHeaders(), bodyLength));
            byte[] response = name.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().putAll(extraResponseHeaders);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    String uri() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<Received> received() {
        return received;
    }

    Received last() {
        return received.get(received.size() - 1);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
