package com.waucaptcha;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/** A local stand-in for the WAU Captcha API: answers each path with a canned reply and keeps every request. */
final class StubService implements AutoCloseable {

    /** A request as the stub received it; header names are lower case. */
    record Request(String method, String path, String query, Map<String, String> headers, String body) {

        String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    /** What a path answers. */
    record Reply(int status, String body, Map<String, String> headers) {

        static Reply json(int status, String body) {
            return new Reply(status, body, Map.of("Content-Type", "application/json"));
        }
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, Function<Request, Reply>> routes = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    StubService() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        // A route that sleeps must not hold up the others.
        server.setExecutor(executor);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    StubService on(String path, Function<Request, Reply> route) {
        routes.put(path, route);
        return this;
    }

    StubService reply(String path, int status, String json) {
        return on(path, request -> Reply.json(status, json));
    }

    List<Request> requests() {
        return requests;
    }

    Request last() {
        return requests.get(requests.size() - 1);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> headers = new HashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), String.join(",", values)));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(), headers, body);
            requests.add(request);
            Function<Request, Reply> route = routes.get(request.path());
            Reply reply = route != null ? route.apply(request)
                    : Reply.json(404, "{\"code\":404,\"reason\":\"ROUTE_NOT_FOUND\",\"message\":\"no service handles this path\",\"metadata\":{}}");
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            reply.headers().forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
            exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
