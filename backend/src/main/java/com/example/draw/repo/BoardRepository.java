package com.example.draw.repo;

import com.example.draw.model.Board;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardRepository extends JpaRepository<Board, UUID> {
    Optional<Board> findByRoomCode(String roomCode);
    boolean existsByRoomCode(String roomCode);
}
