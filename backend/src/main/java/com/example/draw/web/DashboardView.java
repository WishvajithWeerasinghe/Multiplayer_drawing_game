package com.example.draw.web;

import com.example.draw.model.Board;
import com.example.draw.repo.BoardRepository;
import com.example.draw.service.BoardRoomService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Route("")
@PageTitle("Collab Draw | Dashboard")
public class DashboardView extends VerticalLayout implements BeforeEnterObserver {

    private enum Mode {
        WHITEBOARD, GAME
    }

    private final BoardRepository boards;
    private final BoardRoomService boardRooms;
    private boolean inviteDialogOpened;

    public DashboardView(BoardRepository boards, BoardRoomService boardRooms) {
        this.boards = boards;
        this.boardRooms = boardRooms;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        addClassName("dashboard-page");
        getStyle().set("overflow", "auto");
        getStyle().set("background", "#f7f8fc");
        getStyle().set("font-family", "Inter, ui-sans-serif, system-ui, sans-serif");

        Span logoMark = new Span("✳");
        logoMark.getStyle().set("display", "grid");
        logoMark.getStyle().set("place-items", "center");
        logoMark.getStyle().set("width", "38px");
        logoMark.getStyle().set("height", "38px");
        logoMark.getStyle().set("border-radius", "13px");
        logoMark.getStyle().set("background", "#4f46e5");
        logoMark.getStyle().set("color", "#fff");
        logoMark.getStyle().set("font-size", "1.2rem");
        Span logoName = new Span("Collab Draw");
        logoName.getStyle().set("font-size", "1.15rem");
        logoName.getStyle().set("font-weight", "800");
        logoName.getStyle().set("letter-spacing", "-.04em");
        logoName.getStyle().set("color", "#172033");
        HorizontalLayout logo = new HorizontalLayout(logoMark, logoName);
        logo.setAlignItems(Alignment.CENTER);
        logo.setSpacing(true);

        Span liveBadge = new Span("●  REAL-TIME ROOMS");
        liveBadge.getStyle().set("font-size", ".7rem");
        liveBadge.getStyle().set("font-weight", "800");
        liveBadge.getStyle().set("letter-spacing", ".1em");
        liveBadge.getStyle().set("color", "#4338ca");
        liveBadge.getStyle().set("background", "#eef2ff");
        liveBadge.getStyle().set("padding", "9px 13px");
        liveBadge.getStyle().set("border-radius", "999px");
        HorizontalLayout navbar = new HorizontalLayout(logo, liveBadge);
        navbar.setWidthFull();
        navbar.setAlignItems(Alignment.CENTER);
        navbar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        navbar.addClassName("dashboard-navbar");
        navbar.getStyle().set("padding", "18px clamp(20px, 6vw, 88px)");
        navbar.getStyle().set("background", "#fff");
        navbar.getStyle().set("border-bottom", "1px solid #e8edf4");

        Span eyebrow = new Span("MAKE SOMETHING TOGETHER");
        eyebrow.getStyle().set("color", "#c7d2fe");
        eyebrow.getStyle().set("font-size", ".7rem");
        eyebrow.getStyle().set("font-weight", "800");
        eyebrow.getStyle().set("letter-spacing", ".18em");
        H1 headline = new H1("A little creativity goes a long way.");
        headline.getStyle().set("max-width", "640px");
        headline.getStyle().set("font-size", "clamp(2rem, 5vw, 3.5rem)");
        headline.getStyle().set("line-height", "1.06");
        headline.getStyle().set("letter-spacing", "-.055em");
        headline.getStyle().set("color", "#fff");
        headline.getStyle().set("margin", "15px 0 10px");
        Paragraph intro = new Paragraph("Pick a room, invite your people, and let the ideas happen.");
        intro.getStyle().set("color", "#cbd5e1");
        intro.getStyle().set("font-size", "1.05rem");
        intro.getStyle().set("margin", "0");
        VerticalLayout heroText = new VerticalLayout(eyebrow, headline, intro);
        heroText.setPadding(false);
        heroText.setSpacing(false);
        Div hero = new Div(heroText);
        hero.addClassName("dashboard-hero");
        hero.getStyle().set("padding", "clamp(30px, 6vw, 70px)");
        hero.getStyle().set("border-radius", "28px");
        hero.getStyle().set("background", "radial-gradient(ellipse at 85% 20%, rgba(129,140,248,.45), transparent 36%), linear-gradient(120deg, #171a39 0%, #312e81 58%, #4338ca 100%)");
        hero.getStyle().set("box-shadow", "0 22px 60px rgba(49,46,129,.2)");

        H2 chooseTitle = new H2("Choose your room");
        chooseTitle.getStyle().set("margin", "34px 0 5px");
        chooseTitle.getStyle().set("font-size", "1.4rem");
        chooseTitle.getStyle().set("letter-spacing", "-.035em");
        chooseTitle.getStyle().set("color", "#172033");
        Paragraph chooseHint = new Paragraph("Jump back in or start a fresh space with friends.");
        chooseHint.getStyle().set("margin", "0 0 20px");
        chooseHint.getStyle().set("color", "#64748b");

        Div whiteboard = createCard(
                "✎", "#eef2ff", "#4f46e5", "Collaborative whiteboard",
                "Sketch, brainstorm, and see every stroke appear live on a shared canvas.",
                "Open whiteboard", VaadinIcon.ARROW_RIGHT, () -> openEntryDialog(Mode.WHITEBOARD, ""));
        Div game = createCard(
                "?", "#fff7ed", "#ea580c", "Draw & guess",
                "A Gartic-style party room is on the way. Enter a room code to get ready.",
                "Enter a game room", VaadinIcon.ARROW_RIGHT, () -> openEntryDialog(Mode.GAME, ""));
        Div cardGrid = new Div(whiteboard, game);
        cardGrid.addClassName("dashboard-card-grid");
        cardGrid.getStyle().set("display", "grid");
        cardGrid.getStyle().set("gap", "18px");

        Span footer = new Span("Bring a friend. Make a mess. Make something great.");
        footer.getStyle().set("display", "block");
        footer.getStyle().set("text-align", "center");
        footer.getStyle().set("padding", "32px 12px");
        footer.getStyle().set("color", "#94a3b8");
        footer.getStyle().set("font-size", ".85rem");

        VerticalLayout content = new VerticalLayout(hero, chooseTitle, chooseHint, cardGrid, footer);
        content.setWidthFull();
        content.setMaxWidth("1120px");
        content.setPadding(false);
        content.setSpacing(false);
        content.getStyle().set("margin", "0 auto");
        content.getStyle().set("padding", "34px clamp(18px, 5vw, 56px) 0");
        add(navbar, content);
        setFlexGrow(1, content);
    }

    private Div createCard(String iconText, String iconBackground, String iconColor, String title,
                           String description, String actionText, VaadinIcon actionIcon, Runnable action) {
        Span icon = new Span(iconText);
        icon.getStyle().set("display", "grid");
        icon.getStyle().set("place-items", "center");
        icon.getStyle().set("width", "52px");
        icon.getStyle().set("height", "52px");
        icon.getStyle().set("border-radius", "17px");
        icon.getStyle().set("background", iconBackground);
        icon.getStyle().set("color", iconColor);
        icon.getStyle().set("font-size", "1.6rem");
        icon.getStyle().set("font-weight", "800");

        H2 cardTitle = new H2(title);
        cardTitle.getStyle().set("font-size", "1.2rem");
        cardTitle.getStyle().set("margin", "18px 0 6px");
        cardTitle.getStyle().set("letter-spacing", "-.03em");
        cardTitle.getStyle().set("color", "#172033");
        Paragraph cardDescription = new Paragraph(description);
        cardDescription.getStyle().set("min-height", "48px");
        cardDescription.getStyle().set("margin", "0 0 20px");
        cardDescription.getStyle().set("color", "#64748b");
        cardDescription.getStyle().set("line-height", "1.55");
        Button openButton = new Button(actionText, actionIcon.create());
        openButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        openButton.getStyle().set("color", iconColor);
        openButton.getStyle().set("font-weight", "750");
        openButton.addClickListener(event -> action.run());

        Div card = new Div(icon, cardTitle, cardDescription, openButton);
        card.addClassName("dashboard-card");
        card.getStyle().set("padding", "25px");
        card.getStyle().set("background", "#fff");
        card.getStyle().set("border", "1px solid #e8edf4");
        card.getStyle().set("border-radius", "22px");
        card.getStyle().set("box-shadow", "0 8px 26px rgba(30,41,59,.045)");
        card.getStyle().set("transition", "transform .18s ease, box-shadow .18s ease");
        return card;
    }

    private void openEntryDialog(Mode mode, String presetRoom) {
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(false);
        dialog.setWidth("min(460px, calc(100vw - 32px))");
        dialog.setHeaderTitle(mode == Mode.WHITEBOARD ? "Whiteboard room" : "Join a game room");

        Paragraph description = new Paragraph(mode == Mode.WHITEBOARD
                ? "Create a fresh room, or enter a six-digit room code to join your friends."
                : "Pick your player name and enter the room code your friends shared.");
        description.getStyle().set("color", "#64748b");
        description.getStyle().set("margin", "0 0 8px");

        TextField nameField = new TextField("Your name");
        nameField.setPlaceholder("e.g. Alex");
        nameField.setRequiredIndicatorVisible(true);
        nameField.setMaxLength(32);
        nameField.setWidthFull();

        TextField roomField = new TextField("Room login number");
        roomField.setPlaceholder(mode == Mode.WHITEBOARD ? "6 digits (optional for new room)" : "Enter 6-digit room number");
        roomField.setMaxLength(6);
        roomField.setWidthFull();
        roomField.getElement().setAttribute("inputmode", "numeric");
        roomField.getElement().setAttribute("autocomplete", "one-time-code");
        if (!presetRoom.isBlank()) roomField.setValue(presetRoom);

        VerticalLayout form = new VerticalLayout(description, nameField, roomField);
        form.setPadding(false);
        form.setSpacing(true);
        dialog.add(form);

        Button cancel = new Button("Cancel", event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button enter = new Button(mode == Mode.WHITEBOARD ? "Join room" : "Join game room",
                mode == Mode.WHITEBOARD ? VaadinIcon.PENCIL.create() : VaadinIcon.ARROW_RIGHT.create());
        enter.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        enter.addClickListener(event -> {
            String name = nameField.getValue() == null ? "" : nameField.getValue().trim();
            String code = roomField.getValue() == null ? "" : roomField.getValue().trim();
            if (name.isBlank()) {
                nameField.setErrorMessage("Enter the name your friends know you by.");
                nameField.setInvalid(true);
                return;
            }
            nameField.setInvalid(false);
            if (!code.isBlank() && !code.matches("\\d{6}")) {
                roomField.setErrorMessage("Room numbers have exactly 6 digits.");
                roomField.setInvalid(true);
                return;
            }
            roomField.setInvalid(false);
            if (mode == Mode.GAME) {
                if (code.isBlank()) {
                    roomField.setErrorMessage("Enter the game room number.");
                    roomField.setInvalid(true);
                    return;
                }
                dialog.close();
                Notification.show("Draw & guess rooms are coming soon. The whiteboard is ready to play with friends!",
                        4500, Notification.Position.BOTTOM_CENTER);
                return;
            }
            if (code.isBlank()) {
                roomField.setErrorMessage("Enter a six-digit code, or choose Create room.");
                roomField.setInvalid(true);
                return;
            }
            enterWhiteboard(dialog, name, code);
        });
        if (mode == Mode.WHITEBOARD) {
            Button createRoom = new Button("Create room", VaadinIcon.PLUS.create());
            createRoom.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            createRoom.addClickListener(event -> {
                String name = nameField.getValue() == null ? "" : nameField.getValue().trim();
                if (name.isBlank()) {
                    nameField.setErrorMessage("Enter your name before creating a room.");
                    nameField.setInvalid(true);
                    return;
                }
                nameField.setInvalid(false);
                createWhiteboard(dialog, name);
            });
            dialog.getFooter().add(cancel, createRoom, enter);
        } else {
            dialog.getFooter().add(cancel, enter);
        }
        dialog.open();
    }

    private void createWhiteboard(Dialog dialog, String name) {
        try {
            Board board = boardRooms.createRoom(name + "'s whiteboard");
            openWhiteboard(dialog, board, name);
        } catch (RuntimeException exception) {
            Notification.show("Could not create the room: " + exception.getMessage(),
                    4000, Notification.Position.BOTTOM_CENTER);
        }
    }

    private void enterWhiteboard(Dialog dialog, String name, String code) {
        Board board = boards.findByRoomCode(code).orElse(null);
        if (board == null) {
            Notification.show("Room " + code + " was not found. Check the number and try again.",
                    4000, Notification.Position.BOTTOM_CENTER);
            return;
        }
        openWhiteboard(dialog, board, name);
    }

    private void openWhiteboard(Dialog dialog, Board board, String name) {
        dialog.close();
        String safeName = URLEncoder.encode(name, StandardCharsets.UTF_8);
        UI.getCurrent().navigate("board?room=" + board.getRoomCode() + "&name=" + safeName);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (inviteDialogOpened) return;
        String room = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("room", List.of()).stream().findFirst().orElse("");
        if (!room.isBlank()) {
            inviteDialogOpened = true;
            openEntryDialog(Mode.WHITEBOARD, room);
        }
    }
}
