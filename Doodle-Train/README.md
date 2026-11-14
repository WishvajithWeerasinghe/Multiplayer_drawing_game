# Doodle Train

Doodle Train is a multiplayer drawing and guessing game. Players join the same room and pass ideas between one another. A player may draw an idea or write a description of a drawing, depending on the current turn.

## How the game works

1. Start the server and open the game in a browser.
2. Each player enters the same room code and joins with a nickname.
3. The host starts the game after at least two players have joined.
4. Every player writes an initial idea.
5. During each round, players receive another player's latest contribution.
6. If the received contribution is a description, the player draws it.
7. If the received contribution is a drawing, the player writes what they think it shows.
8. The server moves to the next round after all players submit their work.
9. After all rounds are complete, the game shows the full chains in order:
   - original idea
   - drawing or description
   - later drawings and descriptions
10. The host can return everyone to the lobby to start a new game.

Each chain keeps its original idea, so players can see how it changed from one turn to the next. Players only see the contribution needed for their current turn until the final reveal.

## Requirements

- Java JDK 17 or newer
- A modern web browser

Make sure the `java` and `javac` commands work in your terminal before starting the server.

## How to run

1. Open this project folder.
2. Double-click `run.bat`.
3. Wait for the server to start.
4. Open <http://localhost:8080> in your browser.
5. Open the same address in another browser tab or on another computer to add more players.
6. Enter the same room code in every browser.

For another computer on the same network, replace `localhost` with the host computer's local IP address, for example:

```text
http://192.168.1.10:8080
```

The host computer may need to allow port `8080` through its firewall.

## Run manually

If you do not want to use `run.bat`, run these commands from the project folder:

```text
javac -encoding UTF-8 -d out server/GameServer.java
java -cp out GameServer
```

Then open <http://localhost:8080>.

## Notes

- The server keeps room and game data in memory.
- A new player cannot join after the game has started.
- Refreshing or closing the page makes the player leave the room.
- The game does not use accounts or save data after the server stops.
