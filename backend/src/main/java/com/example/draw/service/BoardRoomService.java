package com.example.draw.service;

import com.example.draw.model.Board;
import com.example.draw.repo.BoardRepository;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BoardRoomService {

    private static final int ROOM_CODE_COUNT = 900_000;
    private final BoardRepository boards;
    private final SecureRandom random = new SecureRandom();

    public BoardRoomService(BoardRepository boards) {
        this.boards = boards;
    }

    @Transactional
    public synchronized Board createBoard(String name) {
        String normalizedName = name == null || name.isBlank() ? "Untitled board" : name.trim();
        Board board = new Board(normalizedName);
        board.setRoomCode(nextRoomCode(Set.of()));
        return boards.save(board);
    }

    @Transactional
    public synchronized List<Board> findAllWithRoomCodes(Sort sort) {
        List<Board> result = boards.findAll(sort);
        List<Board> updated = new ArrayList<>();
        Set<String> assigned = new HashSet<>();
        for (Board board : result) {
            if (board.getRoomCode() == null || board.getRoomCode().isBlank()) {
                String code = nextRoomCode(assigned);
                board.setRoomCode(code);
                assigned.add(code);
                updated.add(board);
            }
        }
        if (!updated.isEmpty()) {
            boards.saveAll(updated);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public synchronized Optional<Board> findByRoomCode(String roomCode) {
        if (roomCode == null || !roomCode.matches("\\d{6}")) {
            return Optional.empty();
        }
        return boards.findByRoomCode(roomCode);
    }

    private String nextRoomCode(Set<String> assigned) {
        int start = random.nextInt(ROOM_CODE_COUNT);
        for (int offset = 0; offset < ROOM_CODE_COUNT; offset++) {
            String candidate = String.format(Locale.ROOT, "%06d", 100_000 + (start + offset) % ROOM_CODE_COUNT);
            if (!assigned.contains(candidate) && !boards.existsByRoomCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("All six-digit whiteboard room codes are in use.");
    }
}
