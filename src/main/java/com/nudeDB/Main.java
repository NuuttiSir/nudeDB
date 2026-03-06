package com.nudeDB;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

//TODO: Can switch unrecognized command stuff for exception throwing
//TODO: Might be able to handle just the inputBuffer without making the command variable IDK
public class Main {

    private final static int TABLE_MAX_PAGES = 100;
    private final static int PAGE_SIZE = 4096;

    // NOTE: ROW_SIZE = IDSIZE + USERNAMESIZE + EMAILSIZE IDK
    // TODO: MAKE BETTER LIKE IN C one would
    private static final int ID_SIZE = Long.BYTES;
    private static final int USERNAME_SIZE = 32;
    private static final int EMAIL_SIZE = 255;
    private final static int ROW_SIZE = ID_SIZE + USERNAME_SIZE + EMAIL_SIZE; // 295 bytes

    private final static int ROWS_PER_PAGE = PAGE_SIZE / ROW_SIZE;
    private final static int TABLE_MAX_ROWS = ROWS_PER_PAGE * TABLE_MAX_PAGES;

    private enum MetaCommandResult {
        META_COMMAND_SUCCESS,
        META_COMMAND_UNRECOGNIZED_COMMAND,
        META_COMMAND_EXIT // for the .exit for now
    }

    private enum PrepareResult {
        PREPARE_SUCCESS,
        PREPARE_UNRECOGNIZED_STATEMENT,
        PREPARE_SYNTAX_ERROR
    }

    private enum StatementType {
        STATEMENT_INSERT,
        STATEMENT_SELECT
    }

    private enum ExecuteResult {
        EXECUTE_SUCCESS,
        EXECUTE_TABLE_FULL
    }

    private static class Statement {
        StatementType type;
        Row rowToInsert;
    }

    private static class Row {
        long id;
        String username;
        String email;

        @Override
        public String toString() {
            return String.format("id: %d, username: %s, email: %s", id, username, email);
        }
    }

    // TODO: Change to generic maybe
    private static class Table {
        int num_rows = 0;
        byte[][] pages = new byte[TABLE_MAX_PAGES][];
    }

    public static void main(String[] args) throws IOException {
        Table table = new Table();
        BufferedReader inputBuffer = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            System.out.print("db > ");
            String command = inputBuffer.readLine();

            if (command.trim().startsWith(".")) {
                switch (doMetaCommand(command)) {
                    // NOTE: For the .exit for now might need to change
                    case MetaCommandResult.META_COMMAND_EXIT:
                        return;
                    case MetaCommandResult.META_COMMAND_SUCCESS:
                        continue;
                    case MetaCommandResult.META_COMMAND_UNRECOGNIZED_COMMAND:
                        System.out.printf("Unrecognized command '%s'\n", command);
                        continue;
                }
            }

            Statement statement = new Statement();
            switch (prepareStatement(command, statement)) {
                case PrepareResult.PREPARE_SUCCESS:
                    break;
                case PrepareResult.PREPARE_UNRECOGNIZED_STATEMENT:
                    System.out.printf("Unrecognized keyword at start of '%s'.\n", command);
                    continue;
                case PrepareResult.PREPARE_SYNTAX_ERROR:
                    System.out.println("Syntax error. Could not parse statement.");
                    continue;
            }

            switch (executeStatement(statement, table)) {
                case EXECUTE_SUCCESS:
                    System.out.println("Executed.");
                    continue;
                case EXECUTE_TABLE_FULL:
                    System.out.println("ERROR: Table full.");
                    break;
            }
        }
    }

    private static MetaCommandResult doMetaCommand(String command) {
        if (command.equals(".exit")) {
            return MetaCommandResult.META_COMMAND_EXIT;
        } else {
            return MetaCommandResult.META_COMMAND_UNRECOGNIZED_COMMAND;
        }
    }

    private static PrepareResult prepareStatement(String command, Statement statement) {
        // TODO: Change as in future the insert or select etc is followed by data
        // ignoreCase, commands start to compare, other string to compare to, start of
        // other string to compare, how many chars to compare
        // so if command = insert foo bar
        // it compares 0..6 chars from command to string insert
        if (command.regionMatches(true, 0, "insert", 0, 6)) {
            statement.type = StatementType.STATEMENT_INSERT;
            statement.rowToInsert = new Row();
            // insert looks like insert id username email
            // Split on one or more whitespace characters (\\s+)
            String[] commandParts = command.split("\\s+");
            if (commandParts.length < 4) {
                return PrepareResult.PREPARE_SYNTAX_ERROR;
            }

            try {
                // TODO: Add that string can be too long
                statement.rowToInsert.id = Long.parseLong(commandParts[1]);
                statement.rowToInsert.username = commandParts[2];
                statement.rowToInsert.email = commandParts[3];
            } catch (Exception e) {
                return PrepareResult.PREPARE_SYNTAX_ERROR;
            }

            return PrepareResult.PREPARE_SUCCESS;
        }

        if (command.regionMatches(true, 0, "select", 0, 6)) {
            statement.type = StatementType.STATEMENT_SELECT;
            return PrepareResult.PREPARE_SUCCESS;
        }
        return PrepareResult.PREPARE_UNRECOGNIZED_STATEMENT;
    }

    private static ExecuteResult executeInsert(Statement statement, Table table) {
        if (table.num_rows >= TABLE_MAX_ROWS) {
            return ExecuteResult.EXECUTE_TABLE_FULL;
        }
        Row rowToInsert = statement.rowToInsert;
        serializeRow(rowToInsert, rowSlot(table, table.num_rows));
        table.num_rows += 1;

        return ExecuteResult.EXECUTE_SUCCESS;
    }

    private static ExecuteResult executeSelect(Statement statement, Table table) {
        Row row = new Row();
        for (int i = 0; i < table.num_rows; i++) {
            deserializeRow(rowSlot(table, i), row);
            System.out.println(row.toString());
        }
        return ExecuteResult.EXECUTE_SUCCESS;
    }

    private static ExecuteResult executeStatement(Statement statement, Table table) {
        switch (statement.type) {
            case StatementType.STATEMENT_INSERT:
                return executeInsert(statement, table);
            case StatementType.STATEMENT_SELECT:
                return executeSelect(statement, table);
        }
        // Just so it doesnt cry
        return ExecuteResult.EXECUTE_SUCCESS;
    }

    private static void serializeRow(Row source, ByteBuffer destination) {
        // id
        destination.putLong(source.id);

        // username — write bytes then pad remaining space with zeros
        byte[] usernameBytes = source.username.getBytes(StandardCharsets.UTF_8);
        destination.put(usernameBytes, 0, Math.min(usernameBytes.length, USERNAME_SIZE));
        // Pad with zeros if username is shorter than USERNAME_SIZE
        for (int i = usernameBytes.length; i < USERNAME_SIZE; i++)
            destination.put((byte) 0);

        // email
        byte[] emailBytes = source.email.getBytes(StandardCharsets.UTF_8);
        destination.put(emailBytes, 0, Math.min(emailBytes.length, EMAIL_SIZE));
        for (int i = emailBytes.length; i < EMAIL_SIZE; i++)
            destination.put((byte) 0);
    }

    private static void deserializeRow(ByteBuffer source, Row destination) {
        // id
        destination.id = source.getLong();

        // username — read USERNAME_SIZE bytes, trim null padding
        byte[] usernameBytes = new byte[USERNAME_SIZE];
        source.get(usernameBytes);
        int usernameLen = 0;
        while (usernameLen < USERNAME_SIZE && usernameBytes[usernameLen] != 0)
            usernameLen++;
        destination.username = new String(usernameBytes, 0, usernameLen, StandardCharsets.UTF_8);

        // email
        byte[] emailBytes = new byte[EMAIL_SIZE];
        source.get(emailBytes);
        int emailLen = 0;
        while (emailLen < EMAIL_SIZE && emailBytes[emailLen] != 0)
            emailLen++;
        destination.email = new String(emailBytes, 0, emailLen, StandardCharsets.UTF_8);
    }

    private static ByteBuffer rowSlot(Table table, int row_num) {
        int page_num = row_num / ROWS_PER_PAGE;

        // Lazily allocate the page
        if (table.pages[page_num] == null) {
            table.pages[page_num] = new byte[PAGE_SIZE];
        }

        int row_offset = row_num % ROWS_PER_PAGE;
        int byte_offset = row_offset * ROW_SIZE;

        // Wrap the page array in a ByteBuffer and position it at this row's slot
        ByteBuffer buffer = ByteBuffer.wrap(table.pages[page_num]);
        buffer.position(byte_offset);
        return buffer;
    }
}
