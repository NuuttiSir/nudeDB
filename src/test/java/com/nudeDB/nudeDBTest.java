package com.nudeDB;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class nudeDBTest {

    @Test
    @DisplayName("inserts and retrieves a row")
    void insertsAndRetrievesARow() throws Exception {
        System.setIn(new ByteArrayInputStream("insert 1 user1 person1@example.com\nselect\n.exit\n".getBytes()));
        ByteArrayOutputStream outputCapture = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputCapture));

        Path dbFile = Files.createTempFile("nudedb-", ".db");
        try {
            Main.main(new String[] { dbFile.toString() });
        } catch (Exception e) {
        }

        assertTrue(outputCapture.toString().contains("id: 1, username: user1, email: person1@example.com"));
    }

    @Test
    @DisplayName("Insert one to full table")
    void insertToFullTable() throws Exception {
        StringBuilder commands = new StringBuilder();
        for (int i = 0; i < 1401; i++) {
            commands.append(String.format("insert %d user%d person%d@email.com\n", i, i, i));
        }

        System.setIn(new ByteArrayInputStream(commands.toString().getBytes()));
        ByteArrayOutputStream outputCapture = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputCapture));

        Path dbFile = Files.createTempFile("nudedb-", ".db");
        try {
            Main.main(new String[] { dbFile.toString() });
        } catch (Exception e) {
        }

        assertTrue(outputCapture.toString().contains("ERROR: Table full"));
    }

    @Test
    void insertLongNameAndEmail() throws Exception {
        String longUsername = "a".repeat(32);
        String longEmail = "a".repeat(255);
        String command = String.format("insert 1 %s %s\nselect", longUsername, longEmail);

        System.setIn(new ByteArrayInputStream(command.getBytes()));
        ByteArrayOutputStream outputCapture = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputCapture));

        Path dbFile = Files.createTempFile("nudedb-", ".db");
        try {
            Main.main(new String[] { dbFile.toString() });
        } catch (Exception e) {
        }

        assertTrue(outputCapture.toString()
                .contains(String.format("id: 1, username: %s, email: %s", longUsername, longEmail)));
    }

    @Test
    void insertOneTooLongNameAndEmail() throws Exception {
        String longUsername = "a".repeat(33);
        String longEmail = "a".repeat(256);
        String command = String.format("insert 1 %s %s\nselect", longUsername, longEmail);

        System.setIn(new ByteArrayInputStream(command.getBytes()));
        ByteArrayOutputStream outputCapture = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputCapture));

        Path dbFile = Files.createTempFile("nudedb-", ".db");
        try {
            Main.main(new String[] { dbFile.toString() });
        } catch (Exception e) {
        }

        assertTrue(outputCapture.toString()
                .contains(String.format("String is too long")));
    }

    @Test
    void insertNegativeID() throws Exception {
        String command = "insert -1 foo foo@bar.com";

        System.setIn(new ByteArrayInputStream(command.getBytes()));
        ByteArrayOutputStream outputCapture = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputCapture));

        Path dbFile = Files.createTempFile("nudedb-", ".db");
        try {
            Main.main(new String[] { dbFile.toString() });
        } catch (Exception e) {
        }

        assertTrue(outputCapture.toString().contains("ID must be positive."));

    }

    @Test
    void keepsDataAfterClosingConnection() throws IOException, InterruptedException {
        Path dbFile = Files.createTempFile("nudedb-", ".db");

        Process process1 = new ProcessBuilder("java", "-cp", "target/classes", "com.nudeDB.Main", dbFile.toString())
                .redirectErrorStream(true)
                .start();

        PrintWriter writer1 = new PrintWriter(process1.getOutputStream(), true);
        writer1.println("insert 1 user1 person1@example.com");
        writer1.println(".exit");
        process1.waitFor();

        Process process2 = new ProcessBuilder("java", "-cp", "target/classes", "com.nudeDB.Main", dbFile.toString())
                .redirectErrorStream(true)
                .start();

        PrintWriter writer2 = new PrintWriter(process2.getOutputStream(), true);
        writer2.println("select");
        writer2.println(".exit");
        process2.waitFor();

        String text = new String(process2.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(text.contains("id: 1, username: user1, email: person1@example.com"));
    }
}
