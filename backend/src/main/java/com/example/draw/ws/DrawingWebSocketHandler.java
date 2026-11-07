package com.example.draw.ws;

import com.example.draw.model.Stroke;
import com.example.draw.repo.BoardRepository;
import com.example.draw.repo.StrokeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Protocol (JSON, client -> server):
 *  {type:"draw",   strokeId, color, size, points:[{x,y},{x,y}]}  live segment, relayed only
 *  {type:"stroke", userId, stroke:{id, color, size, points}}      finished stroke, persisted
 *  {type:"clear"}                                                wipes board
 * Server -> client: relayed "draw" / "clear" messages plus {type:"presence", count}
 */
@Component
public class DrawingWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DrawingWebSocketHandler.class);

    private final Map<UUID, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final StrokeRepository strokes;
    private final BoardRepository boards;

    public DrawingWebSocketHandler(ObjectMapper mapper, StrokeRepository strokes, BoardRepository boards) {
        this.mapper = mapper;
        this.strokes = strokes;
        this.boards = boards;
    }

    private UUID boardId(WebSocketSession s) {
        String path = s.getUri().getPath();
        return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        UUID id;
        try {
            id = boardId(session);
        } catch (Exception e) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        if (!boards.existsById(id)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        rooms.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(session);
        broadcastPresence(id);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UUID id = boardId(session);
        JsonNode node = mapper.readTree(message.getPayload());
        String type = node.path("type").asText();

        switch (type) {
            case "draw" -> broadcast(id, session, message.getPayload());
            case "stroke" -> saveStroke(id, node.path("stroke"), node.path("userId").asText(null));
            case "clear" -> {
                strokes.deleteByBoardId(id);
                broadcast(id, session, message.getPayload());
            }
            default -> log.debug("Unknown message type {}", type);
        }
    }

    private void saveStroke(UUID boardId, JsonNode s, String userId) throws IOException {
        if (!s.hasNonNull("id") || !s.path("points").isArray()) return;
        Stroke stroke = new Stroke();
        stroke.setId(s.get("id").asText());
        stroke.setBoardId(boardId);
        stroke.setUserId(userId);
        stroke.setColor(s.path("color").asText("#000000"));
        stroke.setSize(s.path("size").asInt(4));
        stroke.setPoints(mapper.writeValueAsString(s.get("points")));
        strokes.save(stroke);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        try {
            UUID id = boardId(session);
            Set<WebSocketSession> room = rooms.get(id);
            if (room != null) {
                room.remove(session);
                if (room.isEmpty()) rooms.remove(id);
                else broadcastPresence(id);
            }
        } catch (Exception ignored) {}
    }

    private void broadcastPresence(UUID id) {
        Set<WebSocketSession> room = rooms.get(id);
        if (room == null) return;
        ObjectNode n = mapper.createObjectNode();
        n.put("type", "presence");
        n.put("count", room.size());
        broadcast(id, null, n.toString());
    }

    /** Send to everyone in the room except `except` (pass null to send to all). */
    private void broadcast(UUID id, WebSocketSession except, String payload) {
        Set<WebSocketSession> room = rooms.get(id);
        if (room == null) return;
        TextMessage msg = new TextMessage(payload);
        for (WebSocketSession s : room) {
            if (s == except || !s.isOpen()) continue;
            try {
                synchronized (s) { // WebSocketSession.sendMessage is not thread-safe
                    s.sendMessage(msg);
                }
            } catch (IOException e) {
                log.warn("Send failed: {}", e.getMessage());
            }
        }
    }
}
