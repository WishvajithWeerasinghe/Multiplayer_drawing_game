package com.example.draw.repo;

import com.example.draw.model.Stroke;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface StrokeRepository extends JpaRepository<Stroke, String> {
    List<Stroke> findByBoardIdOrderByCreatedAtAsc(UUID boardId);

    @Transactional
    void deleteByBoardId(UUID boardId);
}
