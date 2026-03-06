package com.nudeDB;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class test {
    @Test
    @DisplayName("inserts and retrieves a row")
    void insertsAndRetrievesARow() throws Exception {
        System.setIn(new ByteArrayInputStream("insert 1 user1 person1@example.com\nselect\n.exit\n".getBytes()));
        ByteArrayOutputStream outputCapture = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputCapture));

        try {
            Main.main(new String[] {});
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

        try {
            Main.main(new String[] {});
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

        try {
            Main.main(new String[] {});
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

        try {
            Main.main(new String[] {});
        } catch (Exception e) {
        }

        assertTrue(outputCapture.toString()
                .contains(String.format("String is too long")));
    }
}
