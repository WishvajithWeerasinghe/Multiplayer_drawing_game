package com.example.draw.web;

import com.example.draw.model.Board;
import com.example.draw.repo.BoardRepository;
import com.example.draw.repo.StrokeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.List;
import java.util.UUID;

@Route("board")
@PageTitle("Collaborative Whiteboard")
public class DrawingView extends VerticalLayout implements BeforeEnterObserver {

    private final BoardRepository boards;
    private final StrokeRepository strokes;
    private final ObjectMapper mapper;
    private final DrawingCanvas canvas = new DrawingCanvas();
    private final DrawingTools drawingTools = new DrawingTools();
    private final Span roomNumber = new Span("------");
    private final Span participantName = new Span("Guest");
    private final Span avatar = new Span("G");
    private final Span presence = new Span("Waiting for collaborators");
    private final Span connectionStatus = new Span("Offline");
    private Board activeBoard;

    public DrawingView(BoardRepository boards, StrokeRepository strokes, ObjectMapper mapper) {
        this.boards = boards;
        this.strokes = strokes;
        this.mapper = mapper;
        configurePage();

        drawingTools.setToolListener(settings ->
                canvas.updateTool(settings.color(), settings.size(), settings.eraser()));
        drawingTools.setClearListener(canvas::clearBoard);
        canvas.setPresenceListener(count ->
                presence.setText(count == 1 ? "1 person here" : count + " people here"));
        canvas.setConnectionListener(connected -> {
            connectionStatus.setText(connected ? "LIVE" : "RECONNECTING");
            connectionStatus.getStyle().set("color", connected ? "#15803d" : "#b45309");
        });

        H1 title = new H1("Collab Draw");
        title.getStyle().set("margin", "0");
        title.getStyle().set("font-size", "1.25rem");
        title.getStyle().set("font-weight", "800");
        title.getStyle().set("letter-spacing", "-.04em");
        title.getStyle().set("color", "#172033");
        Span mark = new Span("✳");
        mark.getStyle().set("display", "grid");
        mark.getStyle().set("place-items", "center");
        mark.getStyle().set("width", "38px");
        mark.getStyle().set("height", "38px");
        mark.getStyle().set("border-radius", "13px");
        mark.getStyle().set("background", "#4f46e5");
        mark.getStyle().set("color", "white");
        mark.getStyle().set("font-size", "1.2rem");
        HorizontalLayout brand = new HorizontalLayout(mark, title);
        brand.setAlignItems(Alignment.CENTER);
        brand.setSpacing(true);

        roomNumber.getStyle().set("font-family", "ui-monospace, monospace");
        roomNumber.getStyle().set("font-weight", "800");
        roomNumber.getStyle().set("letter-spacing", ".12em");
        roomNumber.getStyle().set("color", "#4338ca");
        roomNumber.getStyle().set("background", "#eef2ff");
        roomNumber.getStyle().set("padding", "8px 12px");
        roomNumber.getStyle().set("border-radius", "999px");
        Span roomLabel = new Span("ROOM");
        roomLabel.getStyle().set("font-size", ".65rem");
        roomLabel.getStyle().set("font-weight", "800");
        roomLabel.getStyle().set("letter-spacing", ".1em");
        roomLabel.getStyle().set("color", "#94a3b8");
        VerticalLayout roomInfo = new VerticalLayout(roomLabel, roomNumber);
        roomInfo.setPadding(false);
        roomInfo.setSpacing(false);
        roomInfo.setAlignItems(Alignment.END);

        participantName.getStyle().set("font-weight", "700");
        participantName.getStyle().set("color", "#334155");
        avatar.getStyle().set("width", "36px");
        avatar.getStyle().set("height", "36px");
        avatar.getStyle().set("border-radius", "50%");
        avatar.getStyle().set("background", "#dbeafe");
        avatar.getStyle().set("color", "#1d4ed8");
        avatar.getStyle().set("display", "grid");
        avatar.getStyle().set("place-items", "center");
        avatar.getStyle().set("font-weight", "800");
        avatar.setText("G");
        HorizontalLayout userInfo = new HorizontalLayout(avatar, participantName);
        userInfo.setAlignItems(Alignment.CENTER);
        userInfo.setSpacing(true);

        Button shareButton = new Button("Invite", VaadinIcon.LINK.create());
        shareButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        shareButton.addClickListener(event -> copyRoomLink());
        Button backButton = new Button("Dashboard", VaadinIcon.ARROW_LEFT.create());
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        backButton.addClickListener(event -> UI.getCurrent().navigate(""));
        HorizontalLayout actions = new HorizontalLayout(backButton, shareButton, roomInfo, userInfo);
        actions.setAlignItems(Alignment.CENTER);
        actions.setSpacing(true);
        actions.getStyle().set("flex-wrap", "wrap");
        actions.getStyle().set("justify-content", "flex-end");
        actions.addClassName("whiteboard-actions");

        HorizontalLayout header = new HorizontalLayout(brand, actions);
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.getStyle().set("flex-wrap", "wrap");
        header.addClassName("whiteboard-topbar");
        header.getStyle().set("padding", "14px clamp(16px, 3vw, 36px)");
        header.getStyle().set("background", "#fff");
        header.getStyle().set("border-bottom", "1px solid #e8edf4");

        H1 boardTitle = new H1("Shared canvas");
        boardTitle.getStyle().set("font-size", "1.05rem");
        boardTitle.getStyle().set("margin", "0");
        boardTitle.getStyle().set("color", "#1e293b");
        Span boardHint = new Span("Every stroke appears live for everyone in this room.");
        boardHint.getStyle().set("color", "#94a3b8");
        boardHint.getStyle().set("font-size", ".82rem");
        VerticalLayout canvasHeading = new VerticalLayout(boardTitle, boardHint);
        canvasHeading.setPadding(false);
        canvasHeading.setSpacing(false);

        presence.getStyle().set("font-size", ".8rem");
        presence.getStyle().set("color", "#64748b");
        connectionStatus.getStyle().set("font-size", ".68rem");
        connectionStatus.getStyle().set("font-weight", "800");
        connectionStatus.getStyle().set("letter-spacing", ".09em");
        connectionStatus.getStyle().set("color", "#64748b");
        HorizontalLayout boardMeta = new HorizontalLayout(canvasHeading, presence, connectionStatus);
        boardMeta.setWidthFull();
        boardMeta.setAlignItems(Alignment.CENTER);
        boardMeta.setJustifyContentMode(JustifyContentMode.BETWEEN);
        boardMeta.getStyle().set("padding", "16px 20px");
        boardMeta.getStyle().set("background", "#fff");
        boardMeta.getStyle().set("border-bottom", "1px solid #edf0f5");

        Div canvasFrame = new Div(canvas);
        canvasFrame.setWidthFull();
        canvasFrame.addClassName("whiteboard-canvas-frame");
        canvasFrame.getStyle().set("min-width", "0");
        canvasFrame.getStyle().set("min-height", "0");
        canvasFrame.getStyle().set("display", "flex");
        canvasFrame.getStyle().set("align-items", "center");
        canvasFrame.getStyle().set("justify-content", "center");
        canvasFrame.getStyle().set("padding", "clamp(12px, 2vw, 26px)");
        canvasFrame.getStyle().set("background", "#f7f8fc");
        canvasFrame.getStyle().set("border", "1px solid #e7eaf1");
        canvasFrame.getStyle().set("border-radius", "20px");
        canvasFrame.getStyle().set("overflow", "auto");
        canvasFrame.getStyle().set("flex", "1");

        HorizontalLayout workspace = new HorizontalLayout(drawingTools, canvasFrame);
        workspace.setWidthFull();
        workspace.addClassName("whiteboard-workspace");
        workspace.setAlignItems(Alignment.STRETCH);
        workspace.setSpacing(true);
        workspace.getStyle().set("min-height", "0");
        workspace.getStyle().set("flex", "1");
        workspace.getStyle().set("padding", "18px clamp(12px, 3vw, 36px) 24px");
        workspace.getStyle().set("gap", "16px");
        workspace.getStyle().set("box-sizing", "border-box");

        add(header, boardMeta, workspace);
        setFlexGrow(1, workspace);
    }

    private void configurePage() {
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle().set("box-sizing", "border-box");
        getStyle().set("overflow", "hidden");
        addClassName("whiteboard-page");
        getStyle().set("background", "#f7f8fc");
        getStyle().set("font-family", "Inter, ui-sans-serif, system-ui, sans-serif");
    }

    private void copyRoomLink() {
        if (activeBoard == null) return;
        UI ui = UI.getCurrent();
        ui.getPage().executeJs("return window.location.origin").then(String.class, origin -> {
            String link = origin + "/?room=" + activeBoard.getRoomCode();
            ui.getPage().executeJs("navigator.clipboard.writeText($0)", link);
            Notification.show("Invite link copied: " + link, 3500, Notification.Position.BOTTOM_START);
        });
    }

    private void loadBoard(Board board, String name) {
        try {
            ArrayNode storedStrokes = mapper.createArrayNode();
            for (var stroke : strokes.findByBoardIdOrderByCreatedAtAsc(board.getId())) {
                ObjectNode item = storedStrokes.addObject();
                item.put("id", stroke.getId());
                item.put("color", stroke.getColor());
                item.put("size", stroke.getSize());
                item.set("points", mapper.readTree(stroke.getPoints()));
            }
            activeBoard = board;
            roomNumber.setText(board.getRoomCode());
            participantName.setText(name);
            avatar.setText(name.substring(0, 1).toUpperCase());
            canvas.initializeBoard(board.getId().toString(), mapper.writeValueAsString(storedStrokes), name);
        } catch (Exception exception) {
            Notification.show("Could not open this room: " + exception.getMessage());
        }
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var parameters = event.getLocation().getQueryParameters().getParameters();
        String code = parameters.getOrDefault("room", List.of()).stream().findFirst().orElse("");
        String name = parameters.getOrDefault("name", List.of()).stream().findFirst().orElse("Guest").trim();
        if (name.isBlank()) name = "Guest";
        if (!code.matches("\\d{6}")) {
            Notification.show("This room link is invalid.");
            UI.getCurrent().navigate("");
            return;
        }
        String participant = name.length() > 32 ? name.substring(0, 32) : name;
        boards.findByRoomCode(code).ifPresentOrElse(
                board -> loadBoard(board, participant),
                () -> {
                    Notification.show("Room " + code + " was not found. Check the code and try again.");
                    UI.getCurrent().navigate("");
                });
    }
}
