# Categories (Państwa–Miasta) — LAN Multiplayer in Java

A lightweight LAN multiplayer implementation of the classic **Categories** (pl. *Państwa–Miasta*) game, built in **Java** with a simple **JavaFX** GUI and a **TCP** server. Players connect over a local network, get a random starting letter, and race to submit valid answers in several categories. Answers are validated against an on‑disk **SQLite** dictionary.

> Tech: Java **22**, JavaFX **23**, **TCP sockets**, **Gson** (JSON), **SQLite JDBC**, **Maven**

---

## Features
- **LAN multiplayer**: TCP server accepts multiple clients; room/lobby with a 6‑char **game code**.
- **Up to 6 players**, **3 rounds** by default (constants in `Game.java`: `MAX_PLAYERS = 6`, `ROUND_LIMIT = 3`, `CODE_LENGTH = 6`).  
- **Categories backed by SQLite** (`identifier.sqlite`): `animals`, `cities`, `countries`, `foods`, `plants`.
- **Answer validation**: server checks that an answer starts with the current letter and exists in the corresponding DB table (`DatabaseUtils.isAnswerValid(...)`).
- **JavaFX GUI client** (`ClientGUI`) with connect form (host/port/nickname), in‑game screens and final scoreboard.
- **JSON protocol** over TCP using **Gson** (examples: `prompt_nickname`, `welcome`, `create_game`, `game_created`, `lobby`, `update_lobby`, `start_game`, and results messages).
- **Persistent scores** in `game_results.db` (table `GameResults(game_code, player_nickname, score)`).
- **Server logging** to `server_logs.txt` with timestamps and actions.

> The repository also includes a local `javafx-sdk-23.0.1/` directory for convenience. You can run with that SDK or configure JavaFX via Maven/IDE as you prefer.

---

## Architecture
**Client (GUI/CLI)**
- `ClientGUI` — JavaFX app: connects to server (defaults to port `12121`), handles lobby updates and results, renders forms & final scoreboard.
- `Client` — minimal console client (useful for debugging).
- `MenuSystem` / `MenuSystemB` — simple text‑menu flows for the CLI client.

**Server**
- `Server` — TCP server with per‑client handlers and JSON message dispatch. Writes `server_logs.txt`.
- `Game` — room state: players, random letter (`A–Z`), round lifecycle, scores, limits (`MAX_PLAYERS`, `ROUND_LIMIT`).
- `Player` — connects nickname, IP and login time.
- `utils/DatabaseUtils` — SQLite connectivity and answer validation against category tables.
- `db/DatabaseInitializer` — placeholder for DB bootstrap (kept minimal; data is provided in `identifier.sqlite`).

**Data / resources**
- `identifier.sqlite` — dictionary for categories: `animals`, `cities`, `countries(code,name)`, `foods`, `plants`.
- `game_results.db` — score storage (`GameResults` table).
- `server_logs.txt` — rolling log of server events.

---

## Gameplay loop
1. **Host** starts the server and creates a game (**open** or invite‑only).  
2. **Players** connect (host/IP, port), enter nickname and join by **code** (or the host starts a new room).  
3. When the host starts the game, the server:  
   - Picks a **random letter**.  
   - Starts a **round timer** and requests answers for configured categories.  
4. Clients submit answers; the server **validates** them (first letter + dictionary).  
5. After each round, players see **scores**; after `ROUND_LIMIT` rounds, the server sends **final totals** (also saved to SQLite).

---

## Getting started

### Prerequisites
- **JDK 22** (matches `pom.xml`), Maven 3.9+  
- For GUI: **JavaFX 23** (use the bundled `javafx-sdk-23.0.1` or configure via Maven).

### Build
```bash
mvn clean package
```

### Run — Option A (IDE)
- Run `org.example.Server.main()`.
- Run `org.example.ClientGUI.main()` and connect to the server (host/IP, port `12121`, nickname).

### Run — Option B (CLI, JavaFX SDK in repo)
From project root:
```bash
# 1) Start server
java -cp "target/classes;target/dependency/*" org.example.Server

# 2) Start GUI client (Windows example)
java --module-path ".\javafx-sdk-23.0.1\lib" --add-modules javafx.controls,javafx.graphics,javafx.fxml ^
     -cp "target/classes;target/dependency/*" org.example.ClientGUI

# Linux/macOS: replace ; with : and backslashes with slashes
java --module-path "./javafx-sdk-23.0.1/lib" --add-modules javafx.controls,javafx.graphics,javafx.fxml \
     -cp "target/classes:target/dependency/*" org.example.ClientGUI
```

> Default port is **12121**. You can change it in `ClientGUI` / `Client` and `Server` if needed.  
> Make sure `identifier.sqlite` and `game_results.db` are accessible from the **working directory** when you start the Server.

---

## Configuration
- **Port/host**: editable in `ClientGUI`; constants exist in `Client`/`Server` (`12121`).
- **Game limits**: `Game.java` (`MAX_PLAYERS`, `ROUND_LIMIT`, `CODE_LENGTH`).
- **Categories**: backed by SQLite tables; expand by adding tables/rows and mapping them in `DatabaseUtils`.

---

## Known limitations / TODO
- LAN‑only TCP; **no encryption/auth**.
- Server state is in‑memory; restart resets active rooms.
- Dictionary is static (SQLite). Consider plural/synonym handling, diacritics normalization and duplicates policy.
- No in‑game chat.
- Minimal error handling for disconnects.
- Consider Maven‑based JavaFX modules (or Gradle) to avoid local SDK path flags.
- Dockerfile for server would simplify onboarding on LAN.

---

## License
MIT (or choose another license).

---

## Credits
Built by Miłosz Rzyczniak. Classic *Państwa–Miasta* mechanics implemented for learning purposes.
