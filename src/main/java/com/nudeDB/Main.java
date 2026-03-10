package com.nudeDB;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

//TODO: Can switch unrecognized command stuff for exception throwing
//TODO: Might be able to handle just the inputBuffer without making the command variable IDK
//TODO: Check that do I need to change all bytebuffers to ints as we handle pages at the moment so int = pageNum
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
        PREPARE_SYNTAX_ERROR,
        PREPARE_STRING_TOO_LONG,
        PREPARE_NEGATIVE_ID
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
        int id;
        String username;
        String email;

        @Override
        public String toString() {
            return String.format("id: %d, username: %s, email: %s", id, username, email);
        }
    }

    private static class Pager {
        FileChannel fileDescriptor;
        int fileLength;
        byte[][] pages = new byte[TABLE_MAX_PAGES][];
    }

    // TODO: Change to generic maybe
    private static class Table {
        int num_rows = 0;
        Pager pager;
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Must supply a database filename.");
            return;
        }

        String filename = args[0];
        Table table = dbOpen(filename);

        BufferedReader inputBuffer = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            System.out.print("db > ");
            String command = inputBuffer.readLine();

            if (command.trim().startsWith(".")) {
                switch (doMetaCommand(command, table)) {
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
                case PREPARE_STRING_TOO_LONG:
                    System.out.println("String is too long.");
                    continue;
                case PREPARE_NEGATIVE_ID:
                    System.out.println("ID must be positive.");
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

    private static MetaCommandResult doMetaCommand(String command, Table table) throws IOException {
        if (command.equals(".exit")) {
            dbClose(table);
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
            return prepareInsert(command, statement);
        }

        if (command.regionMatches(true, 0, "select", 0, 6)) {
            statement.type = StatementType.STATEMENT_SELECT;
            return PrepareResult.PREPARE_SUCCESS;
        }
        return PrepareResult.PREPARE_UNRECOGNIZED_STATEMENT;
    }

    private static PrepareResult prepareInsert(String command, Statement statement) {
        statement.type = StatementType.STATEMENT_INSERT;
        statement.rowToInsert = new Row();
        // TODO: Check if regex can be switched to just whitespace or one space
        // delimiter
        String[] commandParts = command.split("\\s+");
        String keyword = commandParts[0];
        String idString = commandParts[1];
        String username = commandParts[2];
        String email = commandParts[3];

        if (idString == null || idString.isEmpty()
                || username == null || username.isEmpty()
                || email == null || email.isEmpty()) {
            return PrepareResult.PREPARE_SYNTAX_ERROR;
        }

        int id = Integer.parseInt(idString);
        if (id < 0) {
            return PrepareResult.PREPARE_NEGATIVE_ID;
        }
        if (username.length() > USERNAME_SIZE) {
            return PrepareResult.PREPARE_STRING_TOO_LONG;
        }
        if (email.length() > EMAIL_SIZE) {
            return PrepareResult.PREPARE_STRING_TOO_LONG;
        }

        statement.rowToInsert.id = id;
        statement.rowToInsert.username = username;
        statement.rowToInsert.email = email;

        return PrepareResult.PREPARE_SUCCESS;

    }

    private static ExecuteResult executeInsert(Statement statement, Table table) throws IOException {
        if (table.num_rows >= TABLE_MAX_ROWS) {
            return ExecuteResult.EXECUTE_TABLE_FULL;
        }
        Row rowToInsert = statement.rowToInsert;
        serializeRow(rowToInsert, rowSlot(table, table.num_rows));
        table.num_rows += 1;

        return ExecuteResult.EXECUTE_SUCCESS;
    }

    private static ExecuteResult executeSelect(Statement statement, Table table) throws IOException {
        Row row = new Row();
        for (int i = 0; i < table.num_rows; i++) {
            deserializeRow(rowSlot(table, i), row);
            System.out.println(row.toString());
        }
        return ExecuteResult.EXECUTE_SUCCESS;
    }

    private static ExecuteResult executeStatement(Statement statement, Table table) throws IOException {
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
        destination.putInt(source.id);

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
        destination.id = source.getInt();

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

    // TODO: Change
    private static ByteBuffer rowSlot(Table table, int row_num) throws IOException {
        int page_num = row_num / ROWS_PER_PAGE;
        byte[] page = getPage(table.pager, page_num);
        int row_offset = row_num % ROWS_PER_PAGE;
        int byte_offset = row_offset * ROW_SIZE;

        return ByteBuffer.wrap(page, byte_offset, ROW_SIZE);
    }

    private static Table dbOpen(final String filename) throws IOException {
        Pager pager = pagerOpen(filename);

        int numRows = pager.fileLength / ROW_SIZE;

        Table table = new Table();
        table.pager = pager;
        table.num_rows = numRows;

        return table;
    }

    private static Pager pagerOpen(final String filename) throws IOException {
        FileChannel fileChannel = FileChannel.open(
                Path.of(filename),
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE);

        int fileSize = (int) fileChannel.size();

        Pager pager = new Pager();
        pager.fileDescriptor = fileChannel;
        pager.fileLength = fileSize;

        for (int i = 0; i < TABLE_MAX_PAGES; i++) {
            pager.pages[i] = null;
        }

        return pager;
    }

    private static byte[] getPage(Pager pager, int pageNum) throws IOException {
        if (pageNum > TABLE_MAX_PAGES) {
            System.out.printf("Tried to fetch page number out of bounds. %d < %d", pageNum, TABLE_MAX_PAGES);
            return null;
        }

        if (pager.pages[(int) pageNum] == null) {
            // Cache miss
            byte[] page = new byte[PAGE_SIZE];
            int numPages = pager.fileLength / PAGE_SIZE;

            if (pager.fileLength % PAGE_SIZE != 0) {
                numPages += 1;
            }

            if (pageNum <= numPages) {
                pager.fileDescriptor.position(pageNum * PAGE_SIZE);
                ByteBuffer buffer = ByteBuffer.wrap(page);
                int bytesRead = pager.fileDescriptor.read(buffer);
                if (bytesRead == -1) {
                    System.out.println("Error reading file.");
                    return null;
                }
            }
            pager.pages[(int) pageNum] = page;
        }
        return pager.pages[(int) pageNum];
    }

    private static void dbClose(Table table) throws IOException {
        Pager pager = table.pager;
        int numFullPages = (int) (table.num_rows / ROWS_PER_PAGE);

        for (int i = 0; i < numFullPages; i++) {
            if (pager.pages[i] == null) {
                continue;
            }
            pagerFlush(pager, i, PAGE_SIZE);
            pager.pages[i] = null;
        }

        // There may be a partial page to write to the end of the file
        // This should not be needed after we switch to a B-tree
        int numAdditionalRows = (int) (table.num_rows % ROWS_PER_PAGE);
        if (numAdditionalRows > 0) {
            int pageNum = numFullPages;
            if (pager.pages[pageNum] != null) {
                pagerFlush(pager, pageNum, numAdditionalRows * ROW_SIZE);
                pager.pages[pageNum] = null;
            }
        }

        // int result = close(pager.fileDescriptor);
        pager.fileDescriptor.close();
        // if (result == -1) {
        // System.out.println("Error closing db file.");
        // }
        for (int i = 0; i < TABLE_MAX_PAGES; i++) {
            byte[] page = pager.pages[i];
            if (page != null) {
                pager.pages[i] = null;
            }
        }
    }

    private static void pagerFlush(Pager pager, int pageNum, int size) throws IOException {
        if (pager.pages[pageNum] == null) {
            System.out.println("Tried to flush null page.");
            throw new IllegalStateException("Tried to flush null page");
        }
        pager.fileDescriptor.position(pageNum * PAGE_SIZE);
        ByteBuffer buffer = ByteBuffer.wrap(pager.pages[pageNum], 0, size);
        pager.fileDescriptor.write(buffer);
    }
}
