package org.example;

import java.sql.*;

public class Db {
    private static final String URL = "jdbc:mariadb://localhost:3307/inventory";
    private static final String USER = "root";
    private static final String PASS = "rootpassword";

    static {
        // Creează schema dacă nu există
        try (Connection c = DriverManager.getConnection("jdbc:mariadb://localhost:3307", USER, PASS);
             Statement s = c.createStatement()) {
            s.executeUpdate("CREATE DATABASE IF NOT EXISTS inventory");
        } catch (SQLException e) {
            throw new RuntimeException("Nu pot crea schema: " + e.getMessage(), e);
        }

        // Creează tabelul dacă nu există
        try (Connection c = DriverManager.getConnection(URL, USER, PASS);
             Statement s = c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS products (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    qty INT NOT NULL DEFAULT 0,
                    price DOUBLE NOT NULL DEFAULT 0
                )
            """);
        } catch (SQLException e) {
            throw new RuntimeException("Nu pot crea tabelul: " + e.getMessage(), e);
        }
    }

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
