import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class GameServer {
    private static final int PORT = 8080;
    private static final int MAX_MESSAGE = 8_000_000;
    private static final Map<String, Room> ROOMS = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? PORT : Integer.parseInt(args[0]);
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Doodle Train listening on http://localhost:" + server.getLocalPort());
            while (true) new Client(server.accept()).start();
        }
    }

    private static String kind(int step) {
        return step == 0 ? "write" : step % 2 == 1 ? "draw" : "guess";
    }

    private static final class Entry {
        final Client author;
        final int step;
        final String text;
        final boolean skipped;
        Entry(Client author, int step, String text, boolean skipped) {
            this.author = author; this.step = step; this.text = text; this.skipped = skipped;
        }
        String json() {
            return "{\"playerId\":" + quote(author.id) + ",\"player\":" + quote(author.name)
                + ",\"round\":" + (step + 1) + ",\"kind\":" + quote(kind(step))
                + ",\"text\":" + quote(text) + ",\"skipped\":" + skipped + "}";
        }
    }

    /** All room mutations and phase transitions happen under this room's monitor. */
    private static final class Room {
        final String code;
        final List<Client> clients = new ArrayList<>();
        final List<Client> participants = new ArrayList<>();
        final List<List<Entry>> chains = new ArrayList<>();
        final Map<Client, Entry> submissions = new HashMap<>();
        String state = "lobby", gameId = "";
        int step;
        Room(String code) { this.code = code; }
        void broadcast(String json) { for (Client c : clients) c.deliver(json); }
        void roster() {
            StringJoiner players = new StringJoiner(",", "[", "]");
            for (Client c : clients)
                players.add("{\"id\":" + quote(c.id) + ",\"name\":" + quote(c.name) + "}");
            broadcast("{\"type\":\"roster\",\"players\":" + players + ",\"hostId\":"
                + quote(clients.isEmpty() ? "" : clients.get(0).id) + ",\"state\":" + quote(state) + "}");
        }
        synchronized void handle(Client client, Map<String, String> data) {
            if (!clients.contains(client)) return;
            switch (data.getOrDefault("type", "")) {
                case "start":
                    if (client != clients.get(0)) { client.error("Only the room host can start a game."); return; }
                    if (!state.equals("lobby")) { client.error("Return to the lobby before starting a new game."); return; }
                    if (clients.size() < 2) { client.error("At least two players are needed."); return; }
                    participants.clear(); participants.addAll(clients);
                    chains.clear();
                    for (int i = 0; i < participants.size(); i++) chains.add(new ArrayList<>());
                    gameId = UUID.randomUUID().toString(); step = 0; state = "playing";
                    roster(); beginRound(); break;
                case "submission": submit(client, data); break;
                case "lobby":
                    if (client != clients.get(0) || !state.equals("reveal")) {
                        client.error("The host can return everyone to the lobby after the reveal."); return;
                    }
                    state = "lobby"; submissions.clear(); chains.clear(); participants.clear();
                    broadcast("{\"type\":\"lobby\"}"); roster(); break;
                default: client.error("Unknown game command.");
            }
        }
        // At step s, player p handles chain (p - s) mod N. Each player visits
        // every chain exactly once; adjacent contributions have different authors.
        int chainFor(Client player) {
            return Math.floorMod(participants.indexOf(player) - step, participants.size());
        }
        void beginRound() {
            submissions.clear();
            for (Client c : participants)
                if (!clients.contains(c)) submissions.put(c, new Entry(c, step, "", true));
            for (Client c : clients) {
                List<Entry> chain = chains.get(chainFor(c));
                String assignment = step == 0 ? "null" : chain.get(chain.size() - 1).json();
                c.deliver("{\"type\":\"phase\",\"gameId\":" + quote(gameId)
                    + ",\"round\":" + (step + 1) + ",\"totalRounds\":" + participants.size()
                    + ",\"phase\":" + quote(kind(step)) + ",\"assignment\":" + assignment + "}");
            }
            progress(); advanceIfReady();
        }
        void submit(Client client, Map<String, String> data) {
            if (!state.equals("playing") || !gameId.equals(data.get("gameId"))
                || !Integer.toString(step + 1).equals(data.get("round"))
                || !kind(step).equals(data.get("phase")) || !participants.contains(client)) {
                client.error("This submission belongs to a different round."); return;
            }
            if (submissions.containsKey(client)) return;
            String text = data.getOrDefault("text", "");
            if (kind(step).equals("draw")) {
                if (!validDrawing(text)) { client.error("Please submit a PNG drawing under 4 MB."); return; }
            } else {
                text = text.trim();
                if (text.isEmpty() || text.length() > 120) {
                    client.error("Write between 1 and 120 characters."); return;
                }
            }
            submissions.put(client, new Entry(client, step, text, false));
            client.deliver("{\"type\":\"accepted\",\"gameId\":" + quote(gameId) + ",\"round\":" + (step + 1) + "}");
            progress(); advanceIfReady();
        }
        void progress() {
            long ready = clients.stream().filter(submissions::containsKey).count();
            broadcast("{\"type\":\"progress\",\"gameId\":" + quote(gameId)
                + ",\"round\":" + (step + 1) + ",\"ready\":" + ready + ",\"total\":" + clients.size() + "}");
        }
        void advanceIfReady() {
            if (clients.isEmpty() || submissions.size() != participants.size()) return;
            for (Client c : participants) chains.get(chainFor(c)).add(submissions.get(c));
            step++;
            if (step == participants.size()) {
                state = "reveal"; roster(); broadcast(reveal());
            } else beginRound();
        }
        String reveal() {
            StringJoiner albums = new StringJoiner(",", "[", "]");
            for (int i = 0; i < chains.size(); i++) {
                StringJoiner entries = new StringJoiner(",", "[", "]");
                for (Entry entry : chains.get(i)) entries.add(entry.json());
                albums.add("{\"id\":" + i + ",\"owner\":" + quote(participants.get(i).name)
                    + ",\"entries\":" + entries + "}");
            }
            return "{\"type\":\"reveal\",\"gameId\":" + quote(gameId) + ",\"chains\":" + albums + "}";
        }
    }

    private static boolean validDrawing(String text) {
        String prefix = "data:image/png;base64,";
        if (!text.startsWith(prefix) || text.length() > 4_000_000) return false;
        try {
            byte[] bytes = Base64.getDecoder().decode(text.substring(prefix.length()));
            byte[] signature = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
            return bytes.length > signature.length
                && Arrays.equals(signature, Arrays.copyOf(bytes, signature.length));
        } catch (IllegalArgumentException e) { return false; }
    }

    private static final class Client extends Thread {
        final Socket socket;
        final String id = UUID.randomUUID().toString();
        InputStream in;
        OutputStream out;
        Room room;
        String name = "";
        Client(Socket socket) { this.socket = socket; }
        @Override public void run() {
            try {
                in = socket.getInputStream(); out = socket.getOutputStream();
                if (!handshake()) return;
                String message;
                while ((message = readMessage()) != null) {
                    try {
                        Map<String, String> data = new JsonFields(message).parse();
                        if ("join".equals(data.get("type"))) join(data);
                        else if (room != null) room.handle(this, data);
                        else error("Join a room first.");
                    } catch (IllegalArgumentException e) { error("Invalid game message."); }
                }
            } catch (Exception e) {
                if (!(e instanceof IOException)) System.err.println("Connection error: " + e.getMessage());
            } finally { leave(); try { socket.close(); } catch (IOException ignored) {} }
        }
        void join(Map<String, String> data) {
            if (room != null) { error("You have already joined a room."); return; }
            String code = data.getOrDefault("room", "").trim().toUpperCase(Locale.ROOT);
            String nickname = data.getOrDefault("name", "").trim();
            if (code.isEmpty() || code.length() > 12 || nickname.isEmpty() || nickname.length() > 18) {
                error("Enter a nickname (up to 18 characters) and room code (up to 12)."); return;
            }
            // Registration/removal share a lock order to avoid orphaning a room.
            synchronized (ROOMS) {
                Room target = ROOMS.computeIfAbsent(code, Room::new);
                synchronized (target) {
                    if (target.state.equals("playing")) {
                        error("This room is playing. Join again after the reveal."); return;
                    }
                    room = target; name = nickname; target.clients.add(this);
                    deliver("{\"type\":\"joined\",\"playerId\":" + quote(id) + ",\"room\":" + quote(code) + "}");
                    target.roster();
                    if (target.state.equals("reveal")) deliver(target.reveal());
                }
            }
        }
        void leave() {
            if (room == null) return;
            synchronized (ROOMS) {
                synchronized (room) {
                    room.clients.remove(this);
                    if (room.clients.isEmpty()) { ROOMS.remove(room.code, room); return; }
                    room.roster();
                    if (room.state.equals("playing")) {
                        room.broadcast("{\"type\":\"notice\",\"text\":" + quote(name + " disconnected. Missing turns will be marked in the reveal.") + "}");
                        room.submissions.putIfAbsent(this, new Entry(this, room.step, "", true));
                        room.progress(); room.advanceIfReady();
                    }
                }
            }
        }
        void error(String text) { deliver("{\"type\":\"error\",\"text\":" + quote(text) + "}"); }
        void deliver(String message) {
            try { sendFrame(1, message.getBytes(StandardCharsets.UTF_8)); }
            catch (IOException e) { try { socket.close(); } catch (IOException ignored) {} }
        }
        boolean handshake() throws Exception {
            // BufferedReader can consume bytes belonging to the first WS frame.
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            int tail = 0;
            while (header.size() < 16384) {
                int b = in.read(); if (b < 0) return false;
                header.write(b); tail = (tail << 8) | b;
                if (tail == 0x0d0a0d0a) break;
            }
            if (tail != 0x0d0a0d0a) return false;
            String[] lines = header.toString(StandardCharsets.US_ASCII).split("\r\n");
            String key = null;
            for (String line : lines)
                if (line.toLowerCase(Locale.ROOT).startsWith("sec-websocket-key:")) key = line.substring(line.indexOf(':') + 1).trim();
            if (key == null) { serveFile(lines[0]); return false; }
            String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
            out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush(); return true;
        }
        void serveFile(String request) throws IOException {
            String[] parts = request.split(" ");
            String path = parts.length > 1 ? URLDecoder.decode(parts[1].split("\\?", 2)[0], StandardCharsets.UTF_8) : "/";
            if (path.equals("/")) path = "/index.html";
            Path root = Paths.get("web").toAbsolutePath().normalize();
            Path file = root.resolve(path.substring(1)).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII)); out.flush(); return;
            }
            String type = file.toString().endsWith(".css") ? "text/css; charset=utf-8"
                : file.toString().endsWith(".js") ? "text/javascript; charset=utf-8" : "text/html; charset=utf-8";
            byte[] bytes = Files.readAllBytes(file);
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: " + type + "\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(bytes); out.flush();
        }
        synchronized void sendFrame(int opcode, byte[] bytes) throws IOException {
            out.write(0x80 | opcode);
            if (bytes.length < 126) out.write(bytes.length);
            else if (bytes.length < 65536) { out.write(126); out.write(bytes.length >>> 8); out.write(bytes.length); }
            else { out.write(127); long length = bytes.length; for (int i = 7; i >= 0; i--) out.write((int)(length >>> (8 * i)) & 255); }
            out.write(bytes); out.flush();
        }
        String readMessage() throws IOException {
            ByteArrayOutputStream message = new ByteArrayOutputStream();
            boolean fragmented = false;
            while (true) {
                int b1 = in.read(); if (b1 < 0) return null;
                int b2 = in.read(); if (b2 < 0) return null;
                int opcode = b1 & 15;
                boolean fin = (b1 & 128) != 0;
                if ((b1 & 112) != 0 || (b2 & 128) == 0) throw new IOException("Invalid frame");
                long length = b2 & 127;
                if (length == 126) length = readLength(2);
                else if (length == 127) length = readLength(8);
                if (length < 0 || length > MAX_MESSAGE || length + message.size() > MAX_MESSAGE)
                    throw new IOException("Frame too large");
                if (opcode >= 8 && (!fin || length > 125)) throw new IOException("Invalid control frame");
                byte[] mask = in.readNBytes(4), payload = in.readNBytes((int)length);
                if (mask.length != 4 || payload.length != length) return null;
                for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
                if (opcode == 8) { sendFrame(8, payload); return null; }
                if (opcode == 9) { sendFrame(10, payload); continue; }
                if (opcode == 10) continue;
                if (opcode == 1 && !fragmented) fragmented = true;
                else if (opcode != 0 || !fragmented) throw new IOException("Unsupported frame");
                message.write(payload);
                if (fin) return message.toString(StandardCharsets.UTF_8);
            }
        }
        long readLength(int count) throws IOException {
            long length = 0;
            for (int i = 0; i < count; i++) {
                int b = in.read(); if (b < 0) throw new EOFException();
                length = (length << 8) | b;
            }
            return length;
        }
    }

    private static String quote(String text) {
        StringBuilder json = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"': json.append("\\\""); break;
                case '\\': json.append("\\\\"); break;
                case '\n': json.append("\\n"); break;
                case '\r': json.append("\\r"); break;
                case '\t': json.append("\\t"); break;
                default: if (c < 32) json.append(String.format("\\u%04x", (int)c)); else json.append(c);
            }
        }
        return json.append('"').toString();
    }

    /** Incoming commands are flat JSON objects containing string fields only. */
    private static final class JsonFields {
        final String json;
        int at;
        JsonFields(String json) { this.json = json; }
        void whitespace() { while (at < json.length() && " \r\n\t".indexOf(json.charAt(at)) >= 0) at++; }
        void expect(char c) {
            whitespace();
            if (at >= json.length() || json.charAt(at++) != c) throw new IllegalArgumentException();
        }
        String string() {
            expect('"'); StringBuilder value = new StringBuilder();
            while (at < json.length()) {
                char c = json.charAt(at++);
                if (c == '"') return value.toString();
                if (c < 32) throw new IllegalArgumentException();
                if (c == '\\') {
                    if (at >= json.length()) throw new IllegalArgumentException();
                    char escape = json.charAt(at++);
                    switch (escape) {
                        case '"': case '\\': case '/': c = escape; break;
                        case 'b': c = '\b'; break;
                        case 'f': c = '\f'; break;
                        case 'n': c = '\n'; break;
                        case 'r': c = '\r'; break;
                        case 't': c = '\t'; break;
                        case 'u':
                            if (at + 4 > json.length()) throw new IllegalArgumentException();
                            String hex = json.substring(at, at + 4);
                            if (!hex.matches("[0-9a-fA-F]{4}")) throw new IllegalArgumentException();
                            c = (char)Integer.parseInt(hex, 16); at += 4; break;
                        default: throw new IllegalArgumentException();
                    }
                }
                value.append(c);
            }
            throw new IllegalArgumentException();
        }
        Map<String, String> parse() {
            Map<String, String> fields = new HashMap<>(); expect('{'); whitespace();
            if (at < json.length() && json.charAt(at) != '}') {
                while (true) {
                    String key = string(); expect(':'); String value = string();
                    if (fields.putIfAbsent(key, value) != null) throw new IllegalArgumentException();
                    whitespace();
                    if (at >= json.length()) throw new IllegalArgumentException();
                    if (json.charAt(at) != ',') break;
                    at++;
                }
            }
            expect('}'); whitespace();
            if (at != json.length()) throw new IllegalArgumentException();
            return fields;
        }
    }
}
