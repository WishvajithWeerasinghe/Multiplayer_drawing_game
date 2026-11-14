# Collab Draw

Collab Draw is a browser-based, real-time drawing app. The web interface is
authored in Java with Vaadin Flow; the canvas uses a small JavaScript bridge
for pointer events and WebSocket messages. The Spring Boot backend serves the
UI, REST API, and WebSocket connection. Drawings are saved in PostgreSQL.

## Run locally

Requires Java 17, Maven, Docker Compose, curl, and Bash. From the project root:

```bash
bash run.sh
```

The launcher starts PostgreSQL and the backend, then opens the app in your
default browser. It first tries `http://localhost:8081`; when Windows cannot
reach the WSL localhost forward, it opens the WSL network address instead.
The launcher also adds that WSL address to the allowed browser origins. The
backend starts the Doodle Train game server on port 8080, choosing another
available port automatically if needed. The portal has buttons for the
whiteboard dashboard and game. The first Vaadin build may take a few minutes
while it prepares browser resources. Keep the launcher terminal open while
using the app; press Ctrl+C to stop a backend started by the launcher. The
database remains running. Use `docker compose down` to stop PostgreSQL.

The PostgreSQL container is exposed on host port 5433. Backend logs are saved
to `.run/backend.log`. The whiteboard/portal uses port 8081 by default, and
Doodle Train prefers port 8080. Set `SERVER_PORT` or `GAME_PORT` to change the
preferred ports. An already-running backend on the selected server port is
reused.

To run the services manually, use separate terminals from the project root:

```bash
# 1. Start PostgreSQL
docker compose up -d db

# 2. Start the backend (Vaadin prepares frontend resources on first run)
cd backend
mvn clean spring-boot:run

# 3. Open http://localhost:8081 in a browser
```

From the portal, choose Whiteboard to open the existing board dashboard or
Doodle Train to start a multiplayer drawing-and-guessing game. Creating a
whiteboard assigns it a unique six-digit room code; share the code so others
can join from the dashboard. Game hosts can create a room and share its
six-digit code, while other players join by entering that code. Whiteboard
drawing, brush color and size, eraser, and clear-board actions are synchronized
in real time. The whiteboard dashboard lists recent boards newest first and
shows each room code; its Delete action removes a board and its saved strokes
after confirmation.

## Connect devices over Wi-Fi or hotspot

Connect the host and other devices to the same Wi-Fi network or hotspot, then
run `bash run.sh`. The launcher prints a hotspot URL for each detected Windows
network address. Open the appropriate URL on other devices and enter the
six-digit whiteboard or game room code. On the first WSL2 run, Windows may ask
for Administrator approval to configure port forwarding and a firewall rule
restricted to the local subnet.

## Network protocol

The REST API provides `GET/POST /api/boards` and
`GET /api/boards/{id}/strokes`. Drawing clients connect to
`/ws/boards/{boardId}` and exchange JSON messages:

- `draw`: live stroke segments, relayed to other users
- `stroke`: completed stroke, stored in PostgreSQL
- `clear`: clears the board for all connected users
- `presence`: the number of users connected to a board
