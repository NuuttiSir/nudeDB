import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

//TODO: Can switch unrecognized command stuff for exception throwing
//TODO: Might be able to handle just the inputBuffer without making the command variable IDK
public class Main {

    private enum MetaCommandResult {
        META_COMMAND_SUCCESS,
        META_COMMAND_UNRECOGNIZED_COMMAND
    }

    private enum PrepareResult {
        PREPARE_SUCCESS,
        PREPARE_UNRECOGNIZED_STATEMENT
    }

    private enum StatementType {
        STATEMENT_INSERT,
        STATEMENT_SELECT
    }

    private static class Statement {
        StatementType type;
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
            return PrepareResult.PREPARE_SUCCESS;
        }

        if (command.regionMatches(true, 0, "select", 0, 6)) {
            statement.type = StatementType.STATEMENT_SELECT;
            return PrepareResult.PREPARE_SUCCESS;
        }
        return PrepareResult.PREPARE_UNRECOGNIZED_STATEMENT;
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
}
