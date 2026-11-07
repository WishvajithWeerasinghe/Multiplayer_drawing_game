package com.example.drawclient;

import java.util.List;

public final class Models {
    private Models() {}

    public record Point(double x, double y) {}

    public record StrokeData(String id, String color, int size, List<Point> points) {}

    public record BoardInfo(String id, String name) {
        @Override
        public String toString() { return name; }
    }
}
