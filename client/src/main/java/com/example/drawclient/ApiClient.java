package com.example.drawclient;

import com.example.drawclient.Models.BoardInfo;
import com.example.drawclient.Models.Point;
import com.example.drawclient.Models.StrokeData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** REST calls to the Spring Boot server (java.net.http, no extra libs). */
public class ApiClient {
    private final String baseUrl; // e.g. http://localhost:8080
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public String wsUrl(String boardId) {
        return baseUrl.replaceFirst("^http", "ws") + "/ws/boards/" + boardId;
    }

    private JsonNode send(HttpRequest req) throws Exception {
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300) throw new RuntimeException("HTTP " + res.statusCode());
        return mapper.readTree(res.body());
    }

    public List<BoardInfo> listBoards() throws Exception {
        JsonNode arr = send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/boards")).GET().build());
        List<BoardInfo> out = new ArrayList<>();
        for (JsonNode n : arr) out.add(new BoardInfo(n.get("id").asText(), n.get("name").asText()));
        return out;
    }

    public BoardInfo createBoard(String name) throws Exception {
        String body = mapper.writeValueAsString(Map.of("name", name));
        JsonNode n = send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/boards"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
        return new BoardInfo(n.get("id").asText(), n.get("name").asText());
    }

    public List<StrokeData> getStrokes(String boardId) throws Exception {
        JsonNode arr = send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/boards/" + boardId + "/strokes")).GET().build());
        List<StrokeData> out = new ArrayList<>();
        for (JsonNode s : arr) {
            List<Point> pts = new ArrayList<>();
            for (JsonNode p : s.get("points")) pts.add(new Point(p.get("x").asDouble(), p.get("y").asDouble()));
            out.add(new StrokeData(s.get("id").asText(), s.get("color").asText(), s.get("size").asInt(), pts));
        }
        return out;
    }
}
