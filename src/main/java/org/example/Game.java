package org.example;

import com.google.gson.JsonObject;
import org.example.utils.DatabaseUtils;

import java.time.LocalDateTime;
import java.util.*;

public class Game {
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 6;
    private static final int MAX_PLAYERS = 6;
    private static final int ROUND_LIMIT = 3;
    private boolean roundInProgress = false;
    private int currentRound  = 0;
    private Timer responseTimer;
    private final Map<Player, JsonObject> playerAnswers = Collections.synchronizedMap(new HashMap<>());
    private final Map<Player, Integer> scores = new HashMap<>();
    private char currentLetter;

    private final String id;
    private final LocalDateTime startTime;
    private LocalDateTime endTime;
    private final String code;
    private Player host;
    private final List<Player> players;
    private Type gameType;

    // Enum to define game types
    public enum Type {
        OPEN("Open"),
        CLOSE("Close");

        private final String displayName;

        Type(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Initializes a new game instance with a host player
    public Game(Player host) {
        this.id = generateUniqueId();
        this.startTime = LocalDateTime.now();
        this.code = generateRandomCode();
        this.host = host;
        this.players = new ArrayList<>();
        this.gameType = Type.OPEN; // Default setting to OPEN
    }


    // Generates a unique game ID
    private String generateUniqueId() {
        return "GAME-" + System.currentTimeMillis();
    }

    // Generates a random code for the game
    private String generateRandomCode() {
        Random random = new Random();
        StringBuilder codeBuilder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            codeBuilder.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return codeBuilder.toString();
    }

    // Adds a player to the game if it's not full
    public boolean addPlayer(Player player) {
        if (players.size() < MAX_PLAYERS) {
            players.add(player);
            return true;
        }
        return false;
    }

    // Removes a player from the game
    public boolean removePlayer(Player player) {
        return players.remove(player);
    }

    // Assigns a new host if the current host leaves
    public void assignNewHost() {
        if (!players.isEmpty()) {
            host = players.get(0); // Assigns the earliest added player as host
        } else {
            host = null; // No players left
        }
    }

    // Checks if the game has reached the maximum number of players
    public boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    // Returns the display name of the game type
    public String getGameTypeDisplayName() {
        return gameType.getDisplayName();
    }

    // Sets the game type
    public void setGameType(Type gameType) {
        this.gameType = gameType;
    }

    // Getter for game ID
    public String getId() {
        return id;
    }

    // Getter for game start time
    public LocalDateTime getStartTime() {
        return startTime;
    }

    // Getter for game end time
    public LocalDateTime getEndTime() {
        return endTime;
    }

    // Getter for game code
    public String getCode() {
        return code;
    }

    // Getter for game host
    public Player getHost() {
        return host;
    }

    // Getter for the list of players
    public List<Player> getPlayers() {
        return players;
    }

    // Getter for game type
    public Type getType() {
        return gameType;
    }

    // Returns a list of player nicknames
    public List<String> getPlayerNicknames() {
        List<String> nicknames = new ArrayList<>();
        for (Player player : players) {
            nicknames.add(player.getNickname());
        }
        return nicknames;
    }

    // Sets the end time of the game
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    // Starts a new round, clearing previous answers and setting the round as in progress
    public synchronized boolean  startRound() {
        if (currentRound >= ROUND_LIMIT) {
            return false;
        }

        playerAnswers.clear();
        roundInProgress = true;
        currentRound ++;
        currentLetter = (char) ('A' + new Random().nextInt(26));
        return true;
    }

    // Ends the current round and increments the round number
    public synchronized void endRound() {
        roundInProgress = false;
        if (responseTimer != null) {
            responseTimer.cancel();
        }
    }

    // Adds a player's answer to the current round
    public synchronized void addPlayerAnswer(Player player, JsonObject answers) {
        playerAnswers.put(player, answers);
    }

    // Checks if it's the first answer in the round (to trigger the 5-second warning)
    public synchronized boolean checkFirstAnswer() {
        return playerAnswers.size() == 1;
    }

    // Returns the current round number
    public int getCurrentRound() {
        return currentRound ;
    }

    // Checks if a round is currently in progress
    public synchronized boolean isRoundInProgress() {
        return roundInProgress;
    }

    // Returns the player answers for the current round
    public Map<Player, JsonObject> getPlayerAnswers() {
        return playerAnswers;
    }

    // Starts a response timer (future functionality)
    public void startResponseTimer(Runnable task, int delayMillis) {
        responseTimer = new Timer();
        responseTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                task.run();
            }
        }, delayMillis);
    }

    public Map<Player, Integer> calculateRoundScores(char startingLetter) {
        Map<Player, Integer> roundScores = new HashMap<>();

        // Inicjalizacja wyników dla graczy (dla bieżącej rundy)
        for (Player player : players) {
            roundScores.put(player, 0);
        }

        // Sprawdzenie, czy istnieje przynajmniej jeden komplet odpowiedzi
        if (playerAnswers.isEmpty()) {
            System.out.println("No answers provided this round.");
            return roundScores;
        }

        // Wyciągamy zestaw kategorii z dowolnych odpowiedzi (załóżmy, że wszystkie są takie same)
        JsonObject sampleAnswers = playerAnswers.values().stream().findFirst().orElse(new JsonObject());
        Set<String> categories = sampleAnswers.keySet();  // Country, City, Animal, Plant, Food

        // Iteracja po każdej kategorii
        for (String category : categories) {
            // Grupy odpowiedzi (klucz – tekst odpowiedzi, wartość – lista graczy, którzy ją podali)
            Map<String, List<Player>> answerGroups = new HashMap<>();

            // Zbierzmy odpowiedzi, które przejdą weryfikację (litera + baza)
            for (Map.Entry<Player, JsonObject> entry : playerAnswers.entrySet()) {
                Player currentPlayer = entry.getKey();
                JsonObject answersJson = entry.getValue();

                String answer = answersJson.has(category)
                        ? answersJson.get(category).getAsString()
                        : "";

                // Metoda sprawdzająca literę i obecność w bazie
                if (DatabaseUtils.isAnswerValid(category, answer, startingLetter)) {
                    // Odpowiedź jest poprawna, grupujemy ją
                    answerGroups.computeIfAbsent(answer, k -> new ArrayList<>()).add(currentPlayer);
                } else {
                    // 0 punktów – nie robimy nic, bo pominęliśmy w answerGroups
                    System.out.println("Invalid or not in DB. Player: "
                            + currentPlayer.getNickname() + ", answer: " + answer);
                }
            }

            // Teraz naliczamy punkty za odpowiedzi poprawne
            for (Map.Entry<String, List<Player>> group : answerGroups.entrySet()) {
                List<Player> playersWithSameAnswer = group.getValue();

                // Jeśli odpowiedź unikatowa (tylko jeden gracz), 2 punkty; jeśli wielu graczy podało tę samą odpowiedź – 1 punkt
                int points = (playersWithSameAnswer.size() == 1) ? 2 : 1;

                // Przyznanie punktów za tę kategorię
                for (Player p : playersWithSameAnswer) {
                    int oldScore = roundScores.getOrDefault(p, 0);
                    roundScores.put(p, oldScore + points);
                }
            }
        }

        // Zaktualizowanie łącznych wyników (mapa scores przechowuje sumaryczne wyniki wszystkich rund)
        for (Map.Entry<Player, Integer> entry : roundScores.entrySet()) {
            Player player = entry.getKey();
            int roundScore = entry.getValue();
            scores.put(player, scores.getOrDefault(player, 0) + roundScore);
        }

        // Wyświetlenie punktów w konsoli (do debugowania)
        System.out.println("Final round scores: " + roundScores);
        System.out.println("Updated total scores: " + scores);
        return roundScores;
    }

    // Getter dla wyników graczy
    public Map<Player, Integer> getScores() {
        return scores;
    }

    public char getCurrentLetter() {
        return currentLetter;
    }
    public boolean isGameOver() {
        return currentRound >= ROUND_LIMIT;
    }

}
