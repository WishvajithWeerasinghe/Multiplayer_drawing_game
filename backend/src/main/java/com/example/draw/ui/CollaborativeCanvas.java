package com.example.draw.ui;

import com.example.draw.model.Stroke;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.Tag;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

@Tag("collaborative-canvas")
@JsModule("./collaborative-canvas.js")
public class CollaborativeCanvas extends Component {

    private final UUID boardId;
    private final String savedStrokes;
    private Consumer<Boolean> connectionListener = connected -> {};
    private IntConsumer presenceListener = count -> {};

    public CollaborativeCanvas(UUID boardId, List<Stroke> strokes, ObjectMapper mapper)
            throws JsonProcessingException {
        this.boardId = boardId;
        this.savedStrokes = serializeStrokes(strokes, mapper);
        getElement().getStyle().set("display", "block");
        getElement().getStyle().set("width", "100%");
        addAttachListener(this::configureCanvas);
    }

    public void setConnectionListener(Consumer<Boolean> listener) {
        connectionListener = listener;
    }

    public void setPresenceListener(IntConsumer listener) {
        presenceListener = listener;
    }

    public void setBrush(String color, int size, boolean erasing) {
        getElement().executeJs("this.setBrush($0, $1, $2)", color, size, erasing);
    }

    public void clearBoard() {
        getElement().executeJs("this.clearBoard()");
    }

    @ClientCallable
    public void connectionChanged(boolean connected) {
        connectionListener.accept(connected);
    }

    @ClientCallable
    public void presenceChanged(int count) {
        presenceListener.accept(count);
    }

    private void configureCanvas(AttachEvent event) {
        getElement().executeJs("this.configure($0, $1)", boardId.toString(), savedStrokes);
    }

    private static String serializeStrokes(List<Stroke> strokes, ObjectMapper mapper)
            throws JsonProcessingException {
        ArrayNode result = mapper.createArrayNode();
        for (Stroke stroke : strokes) {
            ObjectNode item = result.addObject();
            item.put("id", stroke.getId());
            item.put("color", stroke.getColor());
            item.put("size", stroke.getSize());
            item.set("points", mapper.readTree(stroke.getPoints()));
        }
        return mapper.writeValueAsString(result);
    }
}
