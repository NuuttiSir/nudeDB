import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

//TODO: Can switch unrecognized command stuff for exception throwing
//TODO: Might be able to handle just the inputBuffer without making the command variable IDK
public class Main {

    private final static int TABLE_MAX_PAGES = 100;

    private enum MetaCommandResult {
        META_COMMAND_SUCCESS,
        META_COMMAND_UNRECOGNIZED_COMMAND
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
    }

    private final static int PAGE_SIZE = 4096;

    // NOTE: ROW_SIZE = IDSIZE + USERNAMESIZE + EMAILSIZE IDK
    // TODO: MAKE BETTER LIKE IN C one would
    private final static int ROW_SIZE = Long.SIZE + 32 + 255;
    private final static int ROWS_PER_PAGE = PAGE_SIZE / ROW_SIZE;
    private final static int TABLE_MAX_ROWS = ROWS_PER_PAGE * TABLE_MAX_PAGES;

    // TODO: Change to generic maybe
    private static class Table {
        int num_rows;
        Object pages[];
    }

    public static void main(String[] args) throws IOException {
        BufferedReader inputBuffer = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("db > ");
            String command = inputBuffer.readLine();

            if (command.startsWith(".")) {
                switch (doMetaCommand(command)) {
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
            }
            executeStatement(statement);
            System.out.println("Executed");
        }
    }

    private static MetaCommandResult doMetaCommand(String command) {
        if (command.equals(".exit")) {
            System.exit(0);
            // For func to not cry for not returning anything
            return MetaCommandResult.META_COMMAND_SUCCESS;
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

            statement.rowToInsert.id = Long.parseLong(commandParts[1]);
            statement.rowToInsert.username = commandParts[2];
            statement.rowToInsert.email = commandParts[3];

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
            System.out.println(row);
        }
        return ExecuteResult.EXECUTE_SUCCESS;
    }

    private static void executeStatement(Statement statement) {
        switch (statement.type) {
            case STATEMENT_INSERT:
                System.out.println("This is where we would do insert");
                break;
            case STATEMENT_SELECT:
                System.out.println("This is where we would do select");
                break;
        }
    }

    private static int row_slot(Table table, int row_num) {
        int page_num = row_num / ROWS_PER_PAGE;
        Object page = table.pages[page_num];
        if (page == null) {
            // IN C would alloate mem.
        }
        int row_offset = row_num % ROWS_PER_PAGE;
        int byte_offset = row_offset * ROW_SIZE;
        return (int) page + byte_offset;

    }
}
