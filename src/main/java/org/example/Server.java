package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.*;


public class Server {

    private static final int PORT = 12121;
    private static final Map<String, Game> activeGames = Collections.synchronizedMap(new HashMap<>());
    private static final Gson gson = new Gson();
    private static final List<ClientHandler> allHandlers = Collections.synchronizedList(new ArrayList<>());
    private static final List<String> categoriesList = List.of("Country", "City", "Animal", "Plant", "Food");
    private static final String LOG_FILE = "server_logs.txt";
    private static final String DB_URL = "jdbc:sqlite:game_results.db";

    static {

    }

    // Entry point for starting the server
    public static void main(String[] args) {
        System.out.println("Server started, listening on port: " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection: " + clientSocket.getInetAddress());

                ClientHandler handler = new ClientHandler(clientSocket);
                allHandlers.add(handler);
                handler.start();
            }
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }

    private static synchronized void log(String message) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            out.println("[" + timestamp + "] " + message);
        } catch (IOException e) {
            System.err.println("Failed to write to log file: " + e.getMessage());
        }
    }

    // ClientHandler class to manage individual client connections and communications
    private static class ClientHandler extends Thread {
        private final Socket clientSocket;
        private Player player;
        private Game currentGame;
        private PrintWriter out;

        // Initialize ClientHandler with the client's socket
        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
            ) {
                this.out = out;

                sendJsonMessage("prompt_nickname", "Please enter your nickname:");
                String nickname = in.readLine();
                if (nickname == null || nickname.trim().isEmpty()) {
                    sendJsonMessage("error", "Invalid nickname.");
                    return;
                }
                String ip = clientSocket.getInetAddress().toString();
                player = new Player(nickname, ip);


                sendJsonMessage("welcome", "Welcome, " + player.getNickname() + "!");
                log("New connection: " + clientSocket.getInetAddress() + ", " + player.getNickname());

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    JsonObject jsonInput = gson.fromJson(inputLine, JsonObject.class);
                    String action = jsonInput.get("action").getAsString();

                    jsonInput.addProperty("ip", clientSocket.getInetAddress().toString());
                    if(player != null){
                        jsonInput.addProperty("nickname",player.getNickname());
                    }

                    if (currentGame != null) {
                        log(currentGame.getCode() + ": " + jsonInput.toString());
                    } else {
                        log(jsonInput.toString());
                    }

                    switch (action) {
                        case "create_game":
                            handleCreateGame(jsonInput);
                            break;
                        case "join_game_random":
                            handleJoinRandomGame();
                            break;
                        case "join_game_by_code":
                            handleJoinGameByCode(jsonInput);
                            break;
                        case "start_game":
                            handleStartGame();
                            break;
                        case "submit_answers":
                            handleSubmitAnswers(jsonInput);
                            break;
                        case "exit":
                            sendJsonMessage("disconnect", "Closing connection...");
                            return;
                        default:
                            sendJsonMessage("error", "Unknown action: " + action);
                    }
                }

                System.out.println("Closing connection with client: " + player.getNickname());
                log("Closing connection with client: " + player.getNickname());
            } catch (IOException e) {
                System.err.println("Communication error with client: " + e.getMessage());
                log("Communication error with client: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing connection: " + e.getMessage());
                    log("Error closing connection: " + e.getMessage());
                }
            }
        }

        // Handles creation of a new game and setting up lobby
        private void handleCreateGame(JsonObject jsonInput) {
            String gameType = jsonInput.get("game_type").getAsString();
            currentGame = new Game(player);

            if (currentGame.addPlayer(player)) {
                activeGames.put(currentGame.getCode(), currentGame);
                currentGame.setGameType("open".equalsIgnoreCase(gameType) ? Game.Type.OPEN : Game.Type.CLOSE);

                sendJsonMessage("game_created", "Created a new game (" + gameType + ") with code: " + currentGame.getCode());
                sendJsonMessage("lobby", "Moved to Lobby. Waiting for other players...");

                System.out.println(currentGame.getCode()+ ": Game created by " + player.getNickname());
                broadcastPlayerList();
            } else {
                sendJsonMessage("error", "Unable to create game.");
            }
        }

        // Handles joining a random open game
        private void handleJoinRandomGame() {
            for (Game game : activeGames.values()) {
                if (game.getType() == Game.Type.OPEN && !game.isFull()) {
                    game.addPlayer(player);
                    currentGame = game;
                    sendJsonMessage("joined_game", "Joined a random game: " + game.getCode());
                    sendJsonMessage("lobby", "Moved to Lobby. Waiting to start...");

                    System.out.println(game.getCode() + ": " + player.getNickname() + " joined the game");
                    broadcastPlayerList();
                    return;
                }
            }
            sendJsonMessage("error", "No open games available. Try again later.");
        }

        // Handles joining a game by a specific code
        private void handleJoinGameByCode(JsonObject jsonInput) {
            String gameCode = jsonInput.get("game_code").getAsString();
            currentGame = activeGames.get(gameCode);

            if (currentGame != null) {
                if (currentGame.addPlayer(player)) {
                    sendJsonMessage("joined_game", "Joined the game: " + gameCode);
                    sendJsonMessage("lobby", "Moved to Lobby. Waiting to start...");
                    System.out.println(currentGame.getCode() + ": " + player.getNickname() + " joined the game");
                    broadcastPlayerList();
                } else {
                    sendJsonMessage("error", "Game is full.");
                }
            } else {
                sendJsonMessage("error", "Game with code " + gameCode + " does not exist.");
            }
        }

        // Broadcasts the current player list to everyone in the game
        private void broadcastPlayerList() {
            JsonObject message = new JsonObject();
            message.addProperty("action", "update_lobby");

            // Pobranie listy pseudonimów zamiast obiektów Player
            List<String> playerNicknames = currentGame.getPlayers().stream()
                    .map(Player::getNickname)
                    .toList();

            message.add("players", gson.toJsonTree(playerNicknames));

            for (ClientHandler handler : getAllHandlersInGame(currentGame)) {
                handler.out.println(message.toString());
            }
        }

        // Sends a JSON message to the client
        private void sendJsonMessage(String action, String message) {
            JsonObject jsonMessage = new JsonObject();
            jsonMessage.addProperty("action", action);
            jsonMessage.addProperty("ip", clientSocket.getInetAddress().toString());
            if(player != null){
                jsonMessage.addProperty("nickname",player.getNickname());
            }
            jsonMessage.addProperty("message", message);

            out.println(jsonMessage.toString());

            if (currentGame != null) {
                log(currentGame.getCode() + ": " + jsonMessage.toString());
            } else {
                log(jsonMessage.toString());
            }
        }

        // Gets all client handlers for the current game
        private List<ClientHandler> getAllHandlersInGame(Game game) {
            List<ClientHandler> handlers = new ArrayList<>();
            for (ClientHandler handler : allHandlers) {
                if (handler.currentGame == game) {
                    handlers.add(handler);
                }
            }
            return handlers;
        }

        // Handles starting a game if there are enough players
        private void handleStartGame() {
            if (currentGame != null && currentGame.getHost().equals(player)) {
                if (currentGame.getPlayers().size() >= 2) {
                    broadcastGameStart();
                    System.out.println(currentGame.getCode() + ": " + "Game started by " + player.getNickname());
                    startNewRound();
                } else {
                    sendJsonMessage("error", "Game requires at least 2 players to start.");
                }
            } else {
                sendJsonMessage("error", "Only the host can start the game.");
            }
        }

        // Broadcasts the game start message to all players in the game
        private void broadcastGameStart() {
            JsonObject message = new JsonObject();
            message.addProperty("action", "game_started");
            message.addProperty("message", "The game has started!");

            for (ClientHandler handler : getAllHandlersInGame(currentGame)) {
                handler.out.println(message.toString());
            }
        }

        private void startNewRound() {
            if (currentGame.startRound()) {
                char letter = currentGame.getCurrentLetter();
                int roundNumber = currentGame.getCurrentRound();

                JsonObject message = new JsonObject();
                message.addProperty("action", "new_round");
                message.addProperty("round_number", roundNumber);
                message.addProperty("letter", String.valueOf(letter));
                message.add("categories", gson.toJsonTree(categoriesList)); // Dodajemy kategorie

                // Wysyłanie wiadomości do wszystkich graczy w grze
                for (ClientHandler handler : getAllHandlersInGame(currentGame)) {
                    handler.out.println(message.toString());
                }

                System.out.println(currentGame.getCode() + ": New round started. Sent round data: " + message);
            }
        }

        // Handles receiving answers from players and logging them
        private void handleSubmitAnswers(JsonObject jsonInput) {
            JsonObject answers = jsonInput.getAsJsonObject("answers");
            currentGame.addPlayerAnswer(player, answers);
            System.out.println(currentGame.getCode() + ": " + "Received answers from player " + player.getNickname() + ": " + answers.toString());

            if (isRoundComplete()) {
                processRoundScores();

                if (currentGame.isGameOver()) {
                    sendFinalScores();
                } else {
                    startNewRound(); // Rozpocznij następną rundę
                }
            }
        }

        private void sendFinalScores() {
            JsonObject finalScoresMessage = new JsonObject();
            finalScoresMessage.addProperty("action", "game_over");
            JsonObject finalScoresJson = new JsonObject();

            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement pstmt = conn.prepareStatement(
                         "INSERT OR REPLACE INTO GameResults (game_code, player_nickname, score) VALUES (?, ?, ?)")) {
                for (Map.Entry<Player, Integer> entry : currentGame.getScores().entrySet()) {
                    String nickname = entry.getKey().getNickname();
                    int score = entry.getValue();

                    // Dodanie wyniku do JSON
                    finalScoresJson.addProperty(nickname, score);

                    // Zapisanie wyniku do bazy danych
                    pstmt.setString(1, currentGame.getCode());
                    pstmt.setString(2, nickname);
                    pstmt.setInt(3, score);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            } catch (SQLException e) {
                log("Error saving scores to database: " + e.getMessage());
            }

            finalScoresMessage.add("scores", finalScoresJson);

            // Wysyłanie wyników do graczy
            for (ClientHandler handler : getAllHandlersInGame(currentGame)) {
                handler.out.println(finalScoresMessage.toString());
            }
            log(currentGame.getCode() + ": Game over. Final scores sent to all players.");
        }


        // Funkcja sprawdza, czy wszyscy gracze udzielili odpowiedzi
        private boolean isRoundComplete() {
            return currentGame.getPlayerAnswers().size() == currentGame.getPlayers().size();
        }

        // Funkcja przetwarza wyniki rundy i aktualizuje wyniki graczy
        private void processRoundScores() {
            char startingLetter = currentGame.getCurrentLetter(); // 'letter' to wylosowana litera
            Map<Player, Integer> roundScores = currentGame.calculateRoundScores(startingLetter);

            // Aktualizuj wyniki graczy w bieżącej grze
            for (Map.Entry<Player, Integer> entry : roundScores.entrySet()) {
                Player player = entry.getKey();
                int roundScore = entry.getValue();
                int totalScore = currentGame.getScores().getOrDefault(player, 0) + roundScore;
                currentGame.getScores().put(player, totalScore); // Zaktualizuj łączny wynik
            }

            sendRoundScores(roundScores, currentGame.getScores()); // Prześlij wyniki
        }


        // Funkcja przesyła wyniki rundy do graczy
        private void sendRoundScores(Map<Player, Integer> roundScores, Map<Player, Integer> totalScores) {
            JsonObject resultsMessage = new JsonObject();
            resultsMessage.addProperty("action", "results");

            // Dodaj wyniki rundy
            JsonObject roundScoresJson = new JsonObject();
            for (Map.Entry<Player, Integer> entry : roundScores.entrySet()) {
                String nickname = entry.getKey().getNickname();
                int roundScore = entry.getValue();
                roundScoresJson.addProperty(nickname, roundScore);
            }
            resultsMessage.add("round_scores", roundScoresJson);

            // Dodaj łączne wyniki
            JsonObject totalScoresJson = new JsonObject();
            for (Map.Entry<Player, Integer> entry : totalScores.entrySet()) {
                String nickname = entry.getKey().getNickname();
                int totalScore = entry.getValue();
                totalScoresJson.addProperty(nickname, totalScore);
            }
            resultsMessage.add("total_scores", totalScoresJson);

            // Wyślij wyniki do wszystkich graczy w grze
            for (ClientHandler handler : getAllHandlersInGame(currentGame)) {
                handler.out.println(resultsMessage.toString());
            }
        }

    }
}
