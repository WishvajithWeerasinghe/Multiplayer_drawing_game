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

## Run with Collab Draw

Start Collab Draw from the repository root using `bash run.sh`, or run the
backend with `mvn -f backend/pom.xml spring-boot:run`. Both services start
together:

- Collab Draw portal and whiteboard: <http://localhost:8081>
- Doodle Train game server: port 8080 by default; if it is occupied, the
  server chooses the next available port.

Choose Doodle Train on the portal, enter your name, and create a room to get a
unique six-digit code. Other players enter their names and that code to join.
For another device on the same Wi-Fi or hotspot, open the host computer's
network address and enter the room code. The host may need to allow the
whiteboard port and selected game port through its firewall. When started
from WSL2, `bash run.sh` can configure the Windows forwarding and local-subnet
firewall rule; approve the one-time Administrator prompt.

## Run the game server by itself

To run only Doodle Train from this folder, double-click `run.bat`. It compiles
the shared game server source and serves the files in `web/` on port 8080, or
the next available port if 8080 is occupied.
Do not run it at the same time as Collab Draw, because the backend starts the
same game server automatically.

The server keeps room and game data in memory. Players cannot join after the
game starts, and refreshing or closing the page makes that player leave.
Game data is not saved after the server stops.
