package com.example.draw.ui;

import com.vaadin.flow.component.button.Button;

final class GameNavigationButton {

    private GameNavigationButton() {}

    static Button create(int gamePort) {
        Button game = new Button("Play Doodle Train");
        game.addClassName("game-button");
        game.addClickListener(event -> event.getSource().getUI().ifPresent(ui ->
                ui.getPage().executeJs(
                        "window.location.assign(window.location.protocol + '//' + window.location.hostname + ':' + $0)",
                        gamePort)));
        return game;
    }
}
