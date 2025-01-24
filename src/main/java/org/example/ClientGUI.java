package org.example;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.application.Platform;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;


    public class ClientGUI extends Application {

        private TextField serverAddressField;
        private TextField portField;
        private TextField nicknameField;
        private TextArea outputArea;
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        private final Gson gson = new Gson();
        private boolean isHost = false;
        private TextArea lobbyPlayersList;
        private Stage primaryStage; // Główne okno aplikacji

        public static void main(String[] args) {
            launch(args);
        }

        @Override
        public void start(Stage primaryStage) {
            this.primaryStage = primaryStage; // Zapisujemy referencję do głównego Stage
            primaryStage.setTitle("Client Application");

            // Create UI components
            serverAddressField = new TextField("localhost");
            portField = new TextField("12121");
            nicknameField = new TextField();
            outputArea = new TextArea();
            outputArea.setEditable(false);

            Button connectButton = new Button("Connect");

            // Layout
            GridPane gridPane = new GridPane();
            gridPane.setHgap(10);
            gridPane.setVgap(10);

            gridPane.add(new Label("Server Address:"), 0, 0);
            gridPane.add(serverAddressField, 1, 0);
            gridPane.add(new Label("Port:"), 0, 1);
            gridPane.add(portField, 1, 1);
            gridPane.add(new Label("Nickname:"), 0, 2);
            gridPane.add(nicknameField, 1, 2);
            gridPane.add(connectButton, 1, 3);
            gridPane.add(outputArea, 0, 4, 2, 1);

            connectButton.setOnAction(e -> connectToServer());

            // Set up the scene
            Scene scene = new Scene(gridPane, 400, 300);
            primaryStage.setScene(scene);
            primaryStage.show();
        }


        private void connectToServer() {
            String serverAddress = serverAddressField.getText();
            int port;
            try {
                port = Integer.parseInt(portField.getText());
            } catch (NumberFormatException e) {
                outputArea.appendText("Invalid port number.\n");
                return;
            }
            String nickname = nicknameField.getText();

            if (nickname.isEmpty()) {
                outputArea.appendText("Please enter a nickname.\n");
                return;
            }

            try {
                socket = new Socket(serverAddress, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                outputArea.appendText("Connected to server at " + serverAddress + ":" + port + "\n");

                String serverMessage = in.readLine();
                if (serverMessage != null) {
                    JsonObject jsonResponse = gson.fromJson(serverMessage, JsonObject.class);
                    String action = jsonResponse.get("action").getAsString();

                    if ("prompt_nickname".equals(action)) {

                        out.println(nickname);
                        outputArea.appendText("Nickname sent to server: " + nickname + "\n");
                        startListeningToServer(); // Rozpocznij nasłuchiwanie
                    }
                }
            } catch (Exception e) {
                outputArea.appendText("Error connecting to server: " + e.getMessage() + "\n");
            }
        }


        private void startListeningToServer() {
            Thread listenerThread = new Thread(() -> {
                try {
                    String response;
                    while ((response = in.readLine()) != null) {
                        System.out.println("Received JSON: " + response); // Logowanie pełnego JSON
                        JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
                        String action = jsonResponse.get("action").getAsString();

                        Platform.runLater(() -> {
                            switch (action) {
                                case "update_lobby":
                                    updateLobbyDisplay(jsonResponse);
                                    break;
                                case "lobby":
                                    enterLobby();
                                    break;
                                case "welcome":
                                    showAlert("Welcome", jsonResponse.get("message").getAsString());
                                    showMainMenu(); // Przenieś do menu głównego
                                    break;
                                case "game_created":
                                    isHost = true;
                                    showAlert("Game Created", jsonResponse.get("message").getAsString());
                                    break;
                                case "game_started":
                                    showAlert("Game Started", jsonResponse.get("message").getAsString());
                                    break;
                                case "new_round":
                                    handleNewRound(jsonResponse);
                                    break;
                                case "results":
                                    handleResults(jsonResponse);
                                    break;
                                case "game_over":
                                    handleGameOver(jsonResponse);
                                    break;

                                default:
                                    showAlert("Server Message", jsonResponse.get("message").getAsString());
                            }
                        });
                    }
                } catch (IOException e) {
                    Platform.runLater(() -> {
                        showAlert("Connection Error", "Lost connection to server.");
                        resetToInitialScreen();
                    });
                } Platform.runLater(() -> showAlert("Error", "Error reading server response"));

            });

            listenerThread.setDaemon(true);
            listenerThread.start();
        }

        private void resetToInitialScreen() {
            Platform.runLater(() -> {
                GridPane gridPane = new GridPane();
                gridPane.setHgap(10);
                gridPane.setVgap(10);

                gridPane.add(new Label("Server Address:"), 0, 0);
                gridPane.add(serverAddressField, 1, 0);
                gridPane.add(new Label("Port:"), 0, 1);
                gridPane.add(portField, 1, 1);
                gridPane.add(new Label("Nickname:"), 0, 2);
                Button connectButton = new Button("Connect");
                gridPane.add(connectButton, 1, 3);
                gridPane.add(outputArea, 0, 4, 2, 1);

                connectButton.setOnAction(e -> connectToServer());

                Scene scene = new Scene(gridPane, 400, 300);
                primaryStage.setScene(scene);
                primaryStage.show();
            });
        }


        private void closeConnection() {
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                outputArea.appendText("Error closing connection: " + e.getMessage() + "\n");
            }
        }


        // Metoda do wyświetlania alertów
        private void showAlert(String title, String message) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        }


        private void showMainMenu() {
            // Clear previous UI components
            GridPane gridPane = new GridPane();
            gridPane.setHgap(10);
            gridPane.setVgap(10);

            Label menuLabel = new Label("Main Menu");
            Button createGameButton = new Button("Create Game");
            Button joinGameButton = new Button("Join Game");
            Button exitButton = new Button("Exit");

            gridPane.add(menuLabel, 0, 0, 2, 1);
            gridPane.add(createGameButton, 0, 1);
            gridPane.add(joinGameButton, 1, 1);
            gridPane.add(exitButton, 0, 2);

            // Set event handlers for buttons
            createGameButton.setOnAction(e -> handleCreateGame());
            joinGameButton.setOnAction(e -> handleJoinGame());
            exitButton.setOnAction(e -> {
                sendExitRequest();
                Platform.exit(); // Zamknięcie aplikacji
            });

            // Update the scene
            Scene scene = new Scene(gridPane, 400, 300);
            Stage stage = (Stage) serverAddressField.getScene().getWindow();
            stage.setScene(scene);
            stage.show();
        }


        private void handleCreateGame() {
            // Tworzenie dialogu z wyborem typu gry
            ChoiceDialog<String> dialog = new ChoiceDialog<>("Open", "Open", "Close");
            dialog.setTitle("Create Game");
            dialog.setHeaderText("Select game type");
            dialog.setContentText("Choose game type:");

            dialog.showAndWait().ifPresent(gameType -> {
                JsonObject request = new JsonObject();
                request.addProperty("action", "create_game");
                request.addProperty("game_type", gameType.toLowerCase()); // Konwertuj na małe litery
                out.println(request.toString());
                outputArea.appendText("Game created as " + gameType + " game. Waiting for players...\n");
            });
        }


        private void handleJoinGame() {
            // Tworzenie dialogu z wyborem opcji dołączenia
            ChoiceDialog<String> dialog = new ChoiceDialog<>("Random Game", "Random Game", "Enter by Code");
            dialog.setTitle("Join Game");
            dialog.setHeaderText("Select join option");
            dialog.setContentText("Choose an option:");

            dialog.showAndWait().ifPresent(joinChoice -> {
                if ("Random Game".equals(joinChoice)) {
                    JsonObject request = new JsonObject();
                    request.addProperty("action", "join_game_random");
                    out.println(request.toString());
                    outputArea.appendText("Request sent to join a random game.\n");
                } else if ("Enter by Code".equals(joinChoice)) {
                    // Wyświetlenie pola tekstowego dla kodu gry
                    TextInputDialog codeDialog = new TextInputDialog();
                    codeDialog.setTitle("Enter Game Code");
                    codeDialog.setHeaderText("Enter the code of the game you want to join");
                    codeDialog.setContentText("Game Code:");

                    codeDialog.showAndWait().ifPresent(gameCode -> {
                        JsonObject request = new JsonObject();
                        request.addProperty("action", "join_game_by_code");
                        request.addProperty("game_code", gameCode);
                        out.println(request.toString());
                        outputArea.appendText("Request sent to join game with code: " + gameCode + "\n");
                    });
                }
            });
        }


        private void sendExitRequest() {
            JsonObject request = new JsonObject();
            request.addProperty("action", "exit");
            out.println(request.toString());
            outputArea.appendText("Disconnected from server.\n");

            // Zamknięcie aplikacji
            Stage stage = (Stage) serverAddressField.getScene().getWindow();
            stage.close();
        }

        private void enterLobby() {
            Platform.runLater(() -> {
                GridPane gridPane = new GridPane();
                gridPane.setHgap(10);
                gridPane.setVgap(10);

                Label lobbyLabel = new Label("Lobby: Waiting for players...");
                Button startGameButton = new Button("Start Game");
                startGameButton.setDisable(!isHost); // Przycisk aktywny tylko dla hosta
                startGameButton.setOnAction(e -> startGame());

                lobbyPlayersList = new TextArea(); // Lista graczy w lobby
                lobbyPlayersList.setEditable(false);

                gridPane.add(lobbyLabel, 0, 0, 2, 1);
                gridPane.add(lobbyPlayersList, 0, 1, 2, 1);
                if (isHost) {
                    gridPane.add(startGameButton, 0, 2);
                }

                // Utworzenie sceny i przypisanie jej do głównego Stage
                Scene scene = new Scene(gridPane, 400, 300);
                primaryStage.setScene(scene);
                primaryStage.show();
            });
        }

        private void updateLobbyDisplay(JsonObject jsonResponse) {
            // Pobranie listy pseudonimów z odpowiedzi serwera
            List<String> players = gson.fromJson(jsonResponse.get("players"), new TypeToken<List<String>>() {}.getType());
            Platform.runLater(() -> {
                lobbyPlayersList.clear();
                players.forEach(player -> lobbyPlayersList.appendText("- " + player + "\n")); // Wyświetlanie samego pseudonimu
            });
        }

        private void startGame() {
            JsonObject request = new JsonObject();
            request.addProperty("action", "start_game");
            out.println(request.toString());
            showAlert("Game Started", "The game has been started!");
        }

        private void showCategoryInputScreen(String letter, List<String> categories, int roundNumber) {
            Platform.runLater(() -> {
                GridPane gridPane = new GridPane();
                gridPane.setHgap(10);
                gridPane.setVgap(10);

                // Etykieta z numerem rundy
                Label roundLabel = new Label("Round " + roundNumber);
                roundLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

                // Etykieta z literą
                Label letterLabel = new Label("Letter: " + letter);
                letterLabel.setStyle("-fx-font-size: 16px;");

                // Timer
                Label timerLabel = new Label("Time: 0");
                timerLabel.setStyle("-fx-font-size: 16px;");
                Timer timer = new Timer(true);
                AtomicInteger timeCounter = new AtomicInteger(0);

                // Ustaw licznik czasu
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        Platform.runLater(() -> timerLabel.setText("Time: " + timeCounter.incrementAndGet()));
                    }
                }, 1000L, 1000L);

                gridPane.add(roundLabel, 0, 0);
                gridPane.add(letterLabel, 1,0);
                gridPane.add(timerLabel, 2, 0);

                // Pola dla kategorii
                TextField[] inputs = new TextField[categories.size()];
                for (int i = 0; i < categories.size(); i++) {
                    Label categoryLabel = new Label(categories.get(i) + ":");
                    TextField inputField = new TextField();
                    inputs[i] = inputField;
                    gridPane.add(categoryLabel, 0, i + 1);
                    gridPane.add(inputField, 1, i + 1);
                }

                // Przycisk do przesyłania odpowiedzi
                Button submitButton = new Button("Submit Answers");
                submitButton.setOnAction(e -> {
                    timer.cancel();
                    submitAnswers(inputs, categories);
                });

                gridPane.add(submitButton, 1, categories.size() + 1);

                // Ustawienie nowej sceny
                Scene scene = new Scene(gridPane, 800, 600);
                primaryStage.setScene(scene);
                primaryStage.show();
            });
        }

        private void submitAnswers(TextField[] inputs, List<String> categories) {
            JsonObject answersJson = new JsonObject();

            // Dodanie odpowiedzi z walidacją
            for (int i = 0; i < categories.size(); i++) {
                String answer = inputs[i].getText().trim();
                if (!answer.isEmpty()) {
                    answersJson.addProperty(categories.get(i), answer);
                }
            }

            JsonObject request = new JsonObject();
            request.addProperty("action", "submit_answers");
            request.add("answers", answersJson);

            // Logowanie danych przed wysłaniem
            System.out.println("Submitting answers: " + request);

            out.println(gson.toJson(request));
            Platform.runLater(() -> showAlert("Answers Submitted", "Your answers have been sent!"));
        }

        private void handleNewRound(JsonObject jsonResponse) {
            int roundNumber = jsonResponse.get("round_number").getAsInt();
            String letter = jsonResponse.get("letter").getAsString();
            List<String> categories = gson.fromJson(jsonResponse.get("categories"), new TypeToken<List<String>>() {}.getType());

            System.out.println("Round " + roundNumber + ". Chosen letter: " + letter);
            System.out.println("Categories: " + categories);

            Platform.runLater(() -> showCategoryInputScreen(letter, categories, roundNumber));
        }


        private void handleResults(JsonObject jsonResponse) {
            JsonObject roundScores = jsonResponse.getAsJsonObject("round_scores");
            JsonObject totalScores = jsonResponse.getAsJsonObject("total_scores");

            StringBuilder resultsMessage = new StringBuilder("Round Results:\n");
            for (String player : roundScores.keySet()) {
                int roundScore = roundScores.get(player).getAsInt();
                int totalScore = totalScores.get(player).getAsInt();
                resultsMessage.append(player)
                        .append(": Round Score = ")
                        .append(roundScore)
                        .append(", Total Score = ")
                        .append(totalScore)
                        .append("\n");
            }

            // Wyświetlenie wyników w oknie alertu
            showAlert("Results", resultsMessage.toString());
        }

        private void handleGameOver(JsonObject jsonResponse) {
            JsonObject scores = jsonResponse.getAsJsonObject("scores");
            StringBuilder results = new StringBuilder("Game Over! Final Scores:\n");

            // Budowanie tekstu z wynikami
            for (String player : scores.keySet()) {
                int score = scores.get(player).getAsInt();
                results.append(player).append(": ").append(score).append(" points\n");
            }

            // Wyświetlanie wyników na głównym oknie
            Platform.runLater(() -> {
                GridPane gridPane = new GridPane();
                gridPane.setHgap(10);
                gridPane.setVgap(10);

                Label resultsLabel = new Label(results.toString());
                Button exitButton = new Button("Exit");

                // Obsługa przycisku Exit
                exitButton.setOnAction(e -> Platform.exit());

                gridPane.add(resultsLabel, 0, 0);
                gridPane.add(exitButton, 0, 1);

                Scene scene = new Scene(gridPane, 400, 300);
                primaryStage.setScene(scene);
                primaryStage.show();
            });
        }

    }
