package com.example.draw.ui;

import com.example.draw.game.GameServer;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("")
@PageTitle("Collab Draw")
@CssImport("./styles.css")
public class HomeView extends VerticalLayout {

    public HomeView(GameServer gameServer) {
        addClassName("app-shell");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        add(createHeader());

        VerticalLayout content = new VerticalLayout();
        content.addClassName("portal-content");
        content.setWidthFull();
        content.setMaxWidth("960px");
        content.setPadding(false);

        Paragraph eyebrow = new Paragraph("MAKE SOMETHING TOGETHER");
        eyebrow.addClassName("eyebrow");
        H1 title = new H1("Choose how to play.");
        title.addClassName("hero-title");
        Paragraph subtitle = new Paragraph("Create together on the whiteboard or pass a drawing along in Doodle Train.");
        subtitle.addClassName("hero-subtitle");

        Div choices = new Div();
        choices.addClassName("portal-choices");
        choices.add(createChoice(
                "CREATE TOGETHER",
                "Whiteboard",
                "Open your shared drawing dashboard and collaborate on a canvas in real time.",
                whiteboardButton()));
        choices.add(createChoice(
                "PLAY WITH FRIENDS",
                "Doodle Train",
                "Join friends for a multiplayer drawing and guessing game.",
                GameNavigationButton.create(gameServer.getPort())));

        content.add(eyebrow, title, subtitle, choices);
        add(content);
    }

    private Button whiteboardButton() {
        Button whiteboard = new Button("Open whiteboard");
        whiteboard.addClassName("primary-button");
        whiteboard.addClickListener(event -> getUI().ifPresent(ui -> ui.navigate("whiteboard")));
        return whiteboard;
    }

    private Div createChoice(String label, String title, String description, Button action) {
        Div card = new Div();
        card.addClassName("portal-card");
        Paragraph category = new Paragraph(label);
        category.addClassName("portal-card-kicker");
        H2 heading = new H2(title);
        Paragraph detail = new Paragraph(description);
        detail.addClassName("portal-card-description");
        card.add(category, heading, detail, action);
        return card;
    }

    private HorizontalLayout createHeader() {
        H2 brand = new H2("collabdraw");
        brand.addClassName("brand");
        HorizontalLayout header = new HorizontalLayout(brand);
        header.addClassName("topbar");
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        return header;
    }
}
