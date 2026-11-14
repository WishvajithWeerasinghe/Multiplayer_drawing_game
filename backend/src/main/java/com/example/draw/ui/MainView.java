package com.example.draw.ui;

import com.example.draw.game.GameServer;
import com.example.draw.model.Board;
import com.example.draw.repo.StrokeRepository;
import com.example.draw.service.BoardDeletionService;
import com.example.draw.service.BoardRoomService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;

@Route("whiteboard")
@PageTitle("Collab Draw")
@CssImport("./styles.css")
public class MainView extends VerticalLayout {

    private final StrokeRepository strokes;
    private final BoardDeletionService boardDeletion;
    private final BoardRoomService boardRooms;
    private final ObjectMapper mapper;
    private final int gamePort;

    public MainView(
            StrokeRepository strokes,
            BoardDeletionService boardDeletion,
            BoardRoomService boardRooms,
            ObjectMapper mapper,
            GameServer gameServer) {
        this.strokes = strokes;
        this.boardDeletion = boardDeletion;
        this.boardRooms = boardRooms;
        this.mapper = mapper;
        this.gamePort = gameServer.getPort();
        addClassName("app-shell");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        showLobby();
    }

    private void showLobby() {
        removeAll();
        add(createHeader());

        VerticalLayout content = new VerticalLayout();
        content.addClassName("lobby-content");
        content.setWidthFull();
        content.setMaxWidth("1120px");
        content.setPadding(false);

        Div hero = new Div();
        hero.addClassName("hero");
        Paragraph eyebrow = new Paragraph("MAKE SOMETHING TOGETHER");
        eyebrow.addClassName("eyebrow");
        H1 title = new H1("A blank canvas. Infinite possibilities.");
        title.addClassName("hero-title");
        Paragraph subtitle = new Paragraph("Create a board and draw together in real time.");
        subtitle.addClassName("hero-subtitle");
        hero.add(eyebrow, title, subtitle);

        Div createPanel = new Div();
        createPanel.addClassName("create-board-panel");
        Paragraph createLabel = new Paragraph("START CREATING");
        createLabel.addClassName("panel-kicker");
        H2 createTitle = new H2("Start a new board");
        createTitle.addClassName("section-title");
        Paragraph createDescription = new Paragraph("Give your shared canvas a name and start drawing.");
        createDescription.addClassName("create-description");

        TextField boardName = new TextField();
        boardName.setPlaceholder("Name your board");
        boardName.setMaxLength(100);
        boardName.setWidthFull();
        Button create = new Button("Create a board");
        create.addClassName("primary-button");
        create.addClickListener(event -> createBoard(boardName.getValue()));
        boardName.addKeyPressListener(Key.ENTER, event -> createBoard(boardName.getValue()));
        HorizontalLayout createRow = new HorizontalLayout(boardName, create);
        createRow.addClassName("create-row");
        createRow.setWidthFull();
        createRow.setAlignItems(Alignment.END);
        createPanel.add(createLabel, createTitle, createDescription, createRow);

        Div joinPanel = new Div();
        joinPanel.addClassName("join-board-panel");
        Paragraph joinLabel = new Paragraph("JOIN YOUR FRIENDS");
        joinLabel.addClassName("panel-kicker");
        H2 joinTitle = new H2("Have a room code?");
        joinTitle.addClassName("section-title");
        Paragraph joinDescription = new Paragraph("Enter the six-digit code shared by the board host.");
        joinDescription.addClassName("create-description");
        TextField roomCode = new TextField();
        roomCode.setLabel("Six-digit room code");
        roomCode.setPlaceholder("123456");
        roomCode.setMaxLength(6);
        roomCode.getElement().setAttribute("inputmode", "numeric");
        roomCode.getElement().setAttribute("autocomplete", "one-time-code");
        Button join = new Button("Join room");
        join.addClassName("primary-button");
        join.addClickListener(event -> joinBoard(roomCode.getValue()));
        roomCode.addKeyPressListener(Key.ENTER, event -> joinBoard(roomCode.getValue()));
        HorizontalLayout joinRow = new HorizontalLayout(roomCode, join);
        joinRow.addClassName("join-board-row");
        joinRow.setWidthFull();
        joinRow.setAlignItems(Alignment.END);
        joinPanel.add(joinLabel, joinTitle, joinDescription, joinRow);

        H2 recentBoards = new H2("Recent boards");
        recentBoards.addClassName("section-title");
        Paragraph recentLabel = new Paragraph("YOUR WORKSPACE");
        recentLabel.addClassName("panel-kicker");
        Div recentTitle = new Div(recentLabel, recentBoards);
        recentTitle.addClassName("recent-title");
        Div recentHeader = new Div();
        recentHeader.addClassName("recent-board-header");
        recentHeader.add(recentTitle);

        Div boardList = new Div();
        boardList.addClassName("board-list");
        boardList.addClassName("recent-board-list");
        try {
            List<Board> recent = boardRooms.findAllWithRoomCodes(Sort.by(Sort.Direction.DESC, "createdAt"));
            recent.forEach(board -> boardList.add(boardCard(board)));
        } catch (DataAccessException exception) {
            Notification.show("Could not load boards: " + exception.getMessage());
        }
        if (boardList.getChildren().findAny().isEmpty()) {
            Paragraph empty = new Paragraph("No boards yet. Create one to get started.");
            empty.addClassName("empty-state");
            boardList.add(empty);
        }

        Div recentPanel = new Div();
        recentPanel.addClassName("recent-boards-panel");
        recentPanel.add(recentHeader, boardList);

        Div dashboardGrid = new Div();
        dashboardGrid.addClassName("dashboard-grid");
        Div boardActions = new Div();
        boardActions.addClassName("board-actions");
        boardActions.add(createPanel, joinPanel);
        dashboardGrid.add(boardActions, recentPanel);

        content.add(hero, dashboardGrid);
        add(content);
    }

    private Div boardCard(Board board) {
        Div card = new Div();
        card.addClassName("board-card");
        Button open = new Button(board.getName(), event -> showBoard(board));
        open.addClassName("board-card-button");
        Paragraph description = new Paragraph("Room code: " + board.getRoomCode() + " · open board");
        description.addClassName("board-card-description");
        String boardName = board.getName();
        Div icon = new Div();
        icon.addClassName("board-card-icon");
        icon.getElement().setAttribute("aria-hidden", "true");
        icon.setText(boardName == null || boardName.isBlank()
                ? "W"
                : boardName.substring(0, 1).toUpperCase(Locale.ROOT));
        Div copy = new Div();
        copy.addClassName("board-card-copy");
        copy.add(open, description);
        Div details = new Div();
        details.addClassName("board-card-details");
        details.add(icon, copy);

        Button delete = new Button("Delete", event -> confirmDeleteBoard(board));
        delete.addClassName("delete-board-button");
        delete.getElement().setAttribute("aria-label", "Delete board " + board.getName());
        card.add(details, delete);
        return card;
    }

    private void confirmDeleteBoard(Board board) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete " + board.getName() + "?");
        dialog.setText("This permanently deletes the board and its saved strokes.");
        dialog.setConfirmText("Delete board");
        dialog.setCancelText("Keep board");
        dialog.setCancelable(true);
        dialog.addConfirmListener(event -> {
            try {
                boardDeletion.deleteBoard(board.getId());
                Notification.show("Deleted " + board.getName());
                showLobby();
            } catch (DataAccessException exception) {
                Notification.show("Could not delete the board: " + exception.getMessage());
            }
        });
        dialog.open();
    }

    private void createBoard(String name) {
        try {
            showBoard(boardRooms.createBoard(name));
        } catch (DataAccessException exception) {
            Notification.show("Could not create board: " + exception.getMessage());
        } catch (IllegalStateException exception) {
            Notification.show("Could not create board: " + exception.getMessage());
        }
    }

    private void joinBoard(String roomCode) {
        String normalizedCode = roomCode == null ? "" : roomCode.trim();
        if (!normalizedCode.matches("\\d{6}")) {
            Notification.show("Enter the six-digit room code.");
            return;
        }
        try {
            Board board = boardRooms.findByRoomCode(normalizedCode).orElse(null);
            if (board == null) {
                Notification.show("No whiteboard was found with that room code.");
                return;
            }
            showBoard(board);
        } catch (DataAccessException exception) {
            Notification.show("Could not join the room: " + exception.getMessage());
        }
    }

    private void showBoard(Board board) {
        removeAll();
        add(createHeader());

        Button back = new Button("← Boards", event -> showLobby());
        back.addClassName("back-button");
        H1 title = new H1(board.getName());
        title.addClassName("board-title");
        Span roomLabel = new Span("ROOM");
        roomLabel.addClassName("room-code-label");
        Span roomNumber = new Span(board.getRoomCode());
        roomNumber.addClassName("room-code-value");
        Button copyRoomCode = new Button("Copy code");
        copyRoomCode.addClassName("copy-room-code-button");
        copyRoomCode.addClickListener(event -> getUI().ifPresent(ui ->
                ui.getPage().executeJs(
                        "if (navigator.clipboard && window.isSecureContext) { navigator.clipboard.writeText($0).catch(() => window.prompt('Copy room code', $0)); } else { window.prompt('Copy room code', $0); }",
                        board.getRoomCode())));
        Div roomCodeBadge = new Div(roomLabel, roomNumber, copyRoomCode);
        roomCodeBadge.addClassName("room-code-badge");
        Div connection = new Div();
        connection.addClassName("connection-status");
        connection.setText("Connecting");
        Div presence = new Div();
        presence.addClassName("presence-status");
        presence.setText("1 person here");

        CollaborativeCanvas canvas;
        try {
            canvas = new CollaborativeCanvas(
                    board.getId(), strokes.findByBoardIdOrderByCreatedAtAsc(board.getId()), mapper);
        } catch (JsonProcessingException exception) {
            Notification.show("Could not load the saved drawing: " + exception.getMessage());
            showLobby();
            return;
        } catch (DataAccessException exception) {
            Notification.show("Could not load the board: " + exception.getMessage());
            showLobby();
            return;
        }
        canvas.setConnectionListener(connected -> {
            connection.setText(connected ? "Connected" : "Reconnecting");
            if (connected) {
                connection.addClassName("connected");
            } else {
                connection.removeClassName("connected");
            }
        });
        canvas.setPresenceListener(count ->
                presence.setText(count + (count == 1 ? " person here" : " people here")));

        TextField colorField = new TextField("Color");
        colorField.setValue("#202127");
        colorField.setMaxLength(7);
        colorField.setWidth("105px");
        TextField brushSize = new TextField("Brush size");
        brushSize.setValue("4");
        brushSize.setMaxLength(2);
        brushSize.setWidth("100px");
        AtomicBoolean erasing = new AtomicBoolean();

        Button eraser = new Button("Eraser");
        eraser.addClickListener(event -> {
            boolean active = !erasing.get();
            erasing.set(active);
            eraser.setText(active ? "Eraser on" : "Eraser");
            applyBrush(canvas, colorField, brushSize, active);
        });
        colorField.addValueChangeListener(event ->
                applyBrush(canvas, colorField, brushSize, erasing.get()));
        brushSize.addValueChangeListener(event ->
                applyBrush(canvas, colorField, brushSize, erasing.get()));

        HorizontalLayout swatches = new HorizontalLayout();
        swatches.addClassName("color-swatches");
        for (String preset : new String[] {
                "#202127", "#ef476f", "#f4a261", "#ffd166", "#06a77d", "#2780e3", "#7657ee"
        }) {
            Button swatch = new Button();
            swatch.addClassName("color-swatch");
            swatch.getElement().getStyle().set("background-color", preset);
            swatch.getElement().setAttribute("aria-label", "Set color " + preset);
            swatch.addClickListener(event -> {
                colorField.setValue(preset);
                applyBrush(canvas, colorField, brushSize, erasing.get());
            });
            swatches.add(swatch);
        }

        Button clear = new Button("Clear board");
        clear.addClassName("clear-button");
        clear.addClickListener(event -> {
            ConfirmDialog dialog = new ConfirmDialog();
            dialog.setHeader("Clear this board?");
            dialog.setText("This will erase the drawing for everyone in the room.");
            dialog.setConfirmText("Clear board");
            dialog.setCancelable(true);
            dialog.addConfirmListener(confirm -> canvas.clearBoard());
            dialog.open();
        });

        HorizontalLayout toolbar = new HorizontalLayout(swatches, colorField, brushSize, eraser, clear);
        toolbar.addClassName("drawing-toolbar");
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);

        HorizontalLayout boardHeader = new HorizontalLayout(back, title, roomCodeBadge, connection, presence);
        boardHeader.addClassName("board-header");
        boardHeader.setWidthFull();
        boardHeader.setAlignItems(Alignment.CENTER);

        canvas.addClassName("drawing-canvas");
        add(boardHeader, toolbar, canvas);
        setFlexGrow(1, canvas);
    }

    private static void applyBrush(
            CollaborativeCanvas canvas, TextField colorField, TextField brushSize, boolean erasing) {
        String color = colorField.getValue().trim();
        if (!color.matches("#[0-9a-fA-F]{6}")) {
            colorField.setInvalid(true);
            colorField.setErrorMessage("Enter a color like #202127");
            return;
        }
        colorField.setInvalid(false);

        try {
            int size = Integer.parseInt(brushSize.getValue().trim());
            if (size < 1 || size > 30) {
                throw new NumberFormatException();
            }
            brushSize.setInvalid(false);
            canvas.setBrush(color, size, erasing);
        } catch (NumberFormatException exception) {
            brushSize.setInvalid(true);
            brushSize.setErrorMessage("Use a size from 1 to 30");
        }
    }

    private HorizontalLayout createHeader() {
        H2 brand = new H2("collabdraw");
        brand.addClassName("brand");
        brand.getStyle().set("margin-right", "auto");
        Button home = new Button("Home", event -> getUI().ifPresent(ui -> ui.navigate("")));
        HorizontalLayout header = new HorizontalLayout(brand, home, GameNavigationButton.create(gamePort));
        header.addClassName("topbar");
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        return header;
    }
}
