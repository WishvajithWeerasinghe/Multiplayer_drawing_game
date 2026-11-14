package com.example.draw.service;

import com.example.draw.repo.BoardRepository;
import com.example.draw.repo.StrokeRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BoardDeletionService {

    private final BoardRepository boards;
    private final StrokeRepository strokes;

    public BoardDeletionService(BoardRepository boards, StrokeRepository strokes) {
        this.boards = boards;
        this.strokes = strokes;
    }

    @Transactional
    public void deleteBoard(UUID boardId) {
        strokes.deleteByBoardId(boardId);
        boards.deleteById(boardId);
    }
}
