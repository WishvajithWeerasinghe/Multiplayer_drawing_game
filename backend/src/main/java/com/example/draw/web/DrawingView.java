package com.example.draw.web;

import com.example.draw.model.Board;
import com.example.draw.repo.BoardRepository;
import com.example.draw.repo.StrokeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.List;
import java.util.UUID;

@Route("")
@PageTitle("Collab Draw")
public class DrawingView extends VerticalLayout implements BeforeEnterObserver {

    private final BoardRepository boards;
    private final StrokeRepository strokes;
    private final ObjectMapper mapper;
    private final String userId = UUID.randomUUID().toString();
    private final ComboBox<Board> boardSelect = new ComboBox<>();
    private List<Board> availableBoards = List.of();
    private final DrawingCanvas canvas = new DrawingCanvas();
    private final DrawingTools drawingTools = new DrawingTools();
    private final Span presence = new Span("Choose or create a board");

    public DrawingView(BoardRepository boards, StrokeRepository strokes, ObjectMapper mapper) {
        this.boards = boards;
        this.strokes = strokes;
        this.mapper = mapper;
        configurePage();

        Span connectionStatus = new Span("Offline");
        presence.getStyle().set("color", "#64748b");
        drawingTools.setToolListener(settings ->
                canvas.updateTool(settings.color(), settings.size(), settings.eraser()));
        canvas.setPresenceListener(count -> presence.setText(count + (count == 1 ? " person here" : " people here")));
        canvas.setConnectionListener(connected -> {
            connectionStatus.setText(connected ? "Live" : "Reconnecting...");
            connectionStatus.getStyle().set("color", connected ? "#15803d" : "#b45309");
        });

        H1 title = new H1("Collab Draw");
        title.getStyle().set("margin", "0");
        title.getStyle().set("font-size", "1.65rem");
        title.getStyle().set("color", "#172033");
        Span subtitle = new Span("Draw together, in real time.");
        subtitle.getStyle().set("color", "#64748b");
        VerticalLayout brand = new VerticalLayout(title, subtitle);
        brand.setPadding(false);
        brand.setSpacing(false);

        boardSelect.setLabel("Board");
        boardSelect.setPlaceholder("Choose a board");
        boardSelect.setItemLabelGenerator(Board::getName);
        boardSelect.setWidth("240px");
        boardSelect.addValueChangeListener(event -> {
            if (event.getValue() != null) loadBoard(event.getValue());
        });

        TextField boardName = new TextField();
        boardName.setPlaceholder("New board name");
        boardName.setAriaLabel("New board name");
        boardName.setWidth("190px");
        Button createBoard = new Button("New board", VaadinIcon.PLUS.create());
        createBoard.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createBoard.addClickListener(event -> {
            try {
                String name = boardName.getValue() == null ? "" : boardName.getValue().trim();
                Board board = boards.save(new Board(name.isEmpty() ? "Untitled board" : name));
                boardName.clear();
                refreshBoards();
                selectBoard(board.getId());
            } catch (RuntimeException exception) {
                Notification.show("Could not create the board: " + exception.getMessage());
            }
        });

        HorizontalLayout boardControls = new HorizontalLayout(boardSelect, boardName, createBoard);
        boardControls.setAlignItems(Alignment.END);
        boardControls.setSpacing(true);
        HorizontalLayout header = new HorizontalLayout(brand, boardControls);
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setFlexGrow(1, brand);

        Button clearButton = new Button("Clear canvas", VaadinIcon.TRASH.create());
        clearButton.addClickListener(event -> canvas.clearBoard());
        HorizontalLayout tools = new HorizontalLayout(drawingTools, clearButton, connectionStatus);
        tools.setWidthFull();
        tools.setAlignItems(Alignment.END);
        tools.setFlexGrow(1, drawingTools);
        tools.setFlexGrow(1, connectionStatus);
        connectionStatus.getStyle().set("text-align", "right");

        Div canvasFrame = new Div(canvas);
        canvasFrame.setWidthFull();
        canvasFrame.getStyle().set("padding", "12px");
        canvasFrame.getStyle().set("background", "rgba(255,255,255,.7)");
        canvasFrame.getStyle().set("border", "1px solid #e2e8f0");
        canvasFrame.getStyle().set("border-radius", "18px");
        add(header, tools, canvasFrame, presence);
        setFlexGrow(1, canvasFrame);
        refreshBoards();
    }

    private void configurePage() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("box-sizing", "border-box");
        getStyle().set("padding", "24px clamp(16px, 4vw, 56px)");
        getStyle().set("background", "linear-gradient(145deg, #f3f6fb, #eaf0f8)");
        getStyle().set("font-family", "Inter, ui-sans-serif, system-ui, sans-serif");
    }

    private void refreshBoards() {
        availableBoards = boards.findAll();
        boardSelect.setItems(availableBoards);
    }

    private void selectBoard(UUID boardId) {
        availableBoards.stream()
                .filter(board -> board.getId().equals(boardId))
                .findFirst()
                .ifPresent(boardSelect::setValue);
    }

    private void loadBoard(Board board) {
        try {
            ArrayNode storedStrokes = mapper.createArrayNode();
            for (var stroke : strokes.findByBoardIdOrderByCreatedAtAsc(board.getId())) {
                ObjectNode item = storedStrokes.addObject();
                item.put("id", stroke.getId());
                item.put("color", stroke.getColor());
                item.put("size", stroke.getSize());
                item.set("points", mapper.readTree(stroke.getPoints()));
            }
            canvas.initializeBoard(board.getId().toString(), mapper.writeValueAsString(storedStrokes), userId);
            presence.setText("Connecting to " + board.getName() + "...");
            UUID boardId = board.getId();
            com.vaadin.flow.component.UI.getCurrent().getPage().executeJs("return window.location.origin")
                    .then(String.class, origin -> boardSelect.setHelperText(
                            "Share this URL with collaborators: " + origin + "/?board=" + boardId));
        } catch (Exception exception) {
            Notification.show("Could not open the board: " + exception.getMessage());
        }
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String boardId = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("board", List.of()).stream().findFirst().orElse(null);
        if (boardId == null) return;
        try {
            selectBoard(UUID.fromString(boardId));
        } catch (IllegalArgumentException exception) {
            Notification.show("The board link is invalid.");
        }
    }
}
