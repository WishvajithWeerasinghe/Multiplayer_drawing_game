package com.example.draw.controller;

import com.example.draw.game.GameServer;
import com.example.draw.model.Board;
import com.example.draw.repo.BoardRepository;
import com.example.draw.repo.StrokeRepository;
import com.example.draw.service.BoardRoomService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/boards")
public class BoardController {

    public record BoardDto(UUID id, String name, String roomCode, String createdAt) {}
    public record StrokeDto(String id, String userId, String color, int size, JsonNode points) {}

    private final BoardRepository boards;
    private final StrokeRepository strokes;
    private final ObjectMapper mapper;
    private final BoardRoomService boardRooms;
    private final GameServer gameServer;

    public BoardController(
            BoardRepository boards,
            StrokeRepository strokes,
            ObjectMapper mapper,
            BoardRoomService boardRooms,
            GameServer gameServer) {
        this.boards = boards;
        this.strokes = strokes;
        this.mapper = mapper;
        this.boardRooms = boardRooms;
        this.gameServer = gameServer;
    }

    private static BoardDto dto(Board b) {
        return new BoardDto(b.getId(), b.getName(), b.getRoomCode(), b.getCreatedAt().toString());
    }

    @GetMapping
    public List<BoardDto> list() {
        return boardRooms.findAllWithRoomCodes(Sort.unsorted())
                .stream().map(BoardController::dto).toList();
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "ui", "vaadin",
                "gamePort", Integer.toString(gameServer.getPort()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BoardDto create(@RequestBody Map<String, String> body) {
        return dto(boardRooms.createBoard(body.get("name")));
    }

    @GetMapping("/room/{roomCode}")
    public BoardDto getByRoomCode(@PathVariable String roomCode) {
        return boardRooms.findByRoomCode(roomCode).map(BoardController::dto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/{id}")
    public BoardDto get(@PathVariable UUID id) {
        return boards.findById(id).map(BoardController::dto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/{id}/strokes")
    public List<StrokeDto> strokes(@PathVariable UUID id) {
        return strokes.findByBoardIdOrderByCreatedAtAsc(id).stream().map(s -> {
            try {
                return new StrokeDto(s.getId(), s.getUserId(), s.getColor(), s.getSize(),
                        mapper.readTree(s.getPoints()));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).toList();
    }
}
