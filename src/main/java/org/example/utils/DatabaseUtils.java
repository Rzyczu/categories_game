package org.example.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class DatabaseUtils {

    // Ścieżka do Twojej bazy danych
    private static final String DB_URL = "jdbc:sqlite:identifier.sqlite";

    // Mapowanie kategorii na nazwy tabel w bazie danych
    private static final Map<String, String> CATEGORY_TABLE_MAP = Map.of(
            "Animal", "animals",
            "City", "cities",
            "Country", "countries",
            "Food", "foods",
            "Plant", "plants"
    );

    /**
     * Metoda sprawdza, czy dana odpowiedź znajduje się w tabeli odpowiadającej kategorii.
     * @param category nazwa kategorii (Animal, City, Country, Food, Plant)
     * @param answer odpowiedź gracza
     * @return true, jeśli istnieje w bazie; false w przeciwnym przypadku
     */
    public static boolean isAnswerInDatabase(String category, String answer) {
        String tableName = CATEGORY_TABLE_MAP.get(category);
        if (tableName == null) {
            // Na wypadek, gdyby kategoria nie była zmapowana
            return false;
        }

        // Zapytanie SQL: sprawdza, czy w danej tabeli istnieje rekord z kolumną 'name' równą answer
        String sql = "SELECT COUNT(*) AS count FROM " + tableName + " WHERE LOWER(name) = LOWER(?)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, answer);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("count");
                    return count > 0; // jeśli > 0, to odpowiedź występuje w bazie
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Metoda sprawdza, czy odpowiedź rozpoczyna się na wymaganą literę oraz czy istnieje w bazie.
     * Zwraca true, jeśli przechodzi obydwie weryfikacje.
     *
     * @param category nazwa kategorii (Animal, City, Country, Food, Plant)
     * @param answer odpowiedź gracza
     * @param startingLetter wylosowana litera rundy
     * @return true, jeśli odpowiedź przechodzi weryfikację; false w przeciwnym razie
     */
    public static boolean isAnswerValid(String category, String answer, char startingLetter) {
        // 1. Sprawdź, czy odpowiedź rozpoczyna się na wymaganą literę
        if (answer.isEmpty() || Character.toLowerCase(answer.charAt(0)) != Character.toLowerCase(startingLetter)) {
            return false;
        }
        // 2. Sprawdź, czy znajduje się w bazie danych
        return isAnswerInDatabase(category, answer);
    }
}
