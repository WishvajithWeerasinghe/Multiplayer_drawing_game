package com.example.drawclient;

import com.example.drawclient.Models.Point;
import com.example.drawclient.Models.StrokeData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** WebSocket connection to one board room, with automatic reconnect. Callbacks arrive on non-UI threads. */
public class DrawSocket implements WebSocket.Listener {

    public interface Handler {
        void onDraw(String color, int size, List<Point> points);
        void onClear();
        void onPresence(int count);
        void onStatus(boolean connected);
    }

    private final String url;
    private final Handler handler;
    private final String userId;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-reconnect");
        t.setDaemon(true);
        return t;
    });
    private final StringBuilder buffer = new StringBuilder();
    private final AtomicBoolean reconnectPending = new AtomicBoolean(false);
    private volatile WebSocket ws;
    private volatile boolean closed;
    private CompletableFuture<Void> lastSend = CompletableFuture.completedFuture(null);

    public DrawSocket(String url, String userId, Handler handler) {
        this.url = url;
        this.userId = userId;
        this.handler = handler;
    }

    public void connect() {
        http.newWebSocketBuilder().buildAsync(URI.create(url), this)
                .whenComplete((w, err) -> { if (err != null) scheduleReconnect(); });
    }

    public void close() {
        closed = true;
        scheduler.shutdownNow();
        WebSocket w = ws;
        if (w != null) w.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
    }

    private void scheduleReconnect() {
        handler.onStatus(false);
        if (closed || !reconnectPending.compareAndSet(false, true)) return;
        scheduler.schedule(() -> { reconnectPending.set(false); connect(); }, 1500, TimeUnit.MILLISECONDS);
    }

    // ---------- sending ----------
    private synchronized void send(ObjectNode msg) {
        WebSocket w = ws;
        if (w == null) return;
        String json = msg.toString();
        // sendText must not be called again before the previous send completes -> chain them
        lastSend = lastSend.thenCompose(v -> w.sendText(json, true))
                .thenApply(x -> (Void) null).exceptionally(e -> null);
    }

    private ArrayNode pointsNode(List<Point> pts) {
        ArrayNode arr = mapper.createArrayNode();
        for (Point p : pts) arr.addObject().put("x", p.x()).put("y", p.y());
        return arr;
    }

    public void sendDraw(String strokeId, String color, int size, List<Point> pts) {
        ObjectNode m = mapper.createObjectNode();
        m.put("type", "draw").put("strokeId", strokeId).put("color", color).put("size", size);
        m.set("points", pointsNode(pts));
        send(m);
    }

    public void sendStroke(StrokeData s) {
        ObjectNode m = mapper.createObjectNode();
        m.put("type", "stroke").put("userId", userId);
        ObjectNode st = m.putObject("stroke");
        st.put("id", s.id()).put("color", s.color()).put("size", s.size());
        st.set("points", pointsNode(s.points()));
        send(m);
    }

    public void sendClear() {
        send(mapper.createObjectNode().put("type", "clear"));
    }

    // ---------- receiving ----------
    @Override
    public void onOpen(WebSocket w) {
        ws = w;
        handler.onStatus(true);
        w.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
        buffer.append(data);
        if (last) {
            String text = buffer.toString();
            buffer.setLength(0);
            try { handle(mapper.readTree(text)); } catch (Exception e) { e.printStackTrace(); }
        }
        w.request(1);
        return null;
    }

    private void handle(JsonNode n) {
        switch (n.path("type").asText()) {
            case "draw" -> {
                List<Point> pts = new ArrayList<>();
                for (JsonNode p : n.get("points")) pts.add(new Point(p.get("x").asDouble(), p.get("y").asDouble()));
                handler.onDraw(n.get("color").asText(), n.get("size").asInt(), pts);
            }
            case "clear" -> handler.onClear();
            case "presence" -> handler.onPresence(n.get("count").asInt());
            default -> { }
        }
    }

    @Override
    public CompletionStage<?> onClose(WebSocket w, int statusCode, String reason) {
        ws = null;
        scheduleReconnect();
        return null;
    }

    @Override
    public void onError(WebSocket w, Throwable error) {
        ws = null;
        scheduleReconnect();
    }
}
