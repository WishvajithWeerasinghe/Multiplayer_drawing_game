package com.example.draw.service;

import com.example.draw.model.Board;
import com.example.draw.repo.BoardRepository;
import java.security.SecureRandom;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class BoardRoomService {

    private static final int ROOM_CODE_ATTEMPTS = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BoardRepository boards;

    public BoardRoomService(BoardRepository boards) {
        this.boards = boards;
    }

    public Board createRoom(String name) {
        for (int attempt = 0; attempt < ROOM_CODE_ATTEMPTS; attempt++) {
            String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
            if (!boards.existsByRoomCode(code)) {
                try {
                    return boards.saveAndFlush(new Board(name, code));
                } catch (DataIntegrityViolationException exception) {
                    if (!boards.existsByRoomCode(code)) throw exception;
                }
            }
        }
        throw new IllegalStateException("Could not allocate a room code. Please try again.");
    }
}
