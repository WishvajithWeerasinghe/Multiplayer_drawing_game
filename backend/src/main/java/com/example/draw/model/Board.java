package com.example.draw.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "boards")
public class Board {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 6, unique = true)
    private String roomCode;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Board() {}
    public Board(String name) { this.name = name; }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
    public Instant getCreatedAt() { return createdAt; }
}
