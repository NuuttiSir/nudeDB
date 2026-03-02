import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Main {

    public static void main() throws IOException {
        BufferedReader inputBuffer = new BufferedReader(new InputStreamReader(System.in));
        Boolean running = true;
        while (running) {
            System.out.print("db > ");
            String command = inputBuffer.readLine();

            if (command.equals(".exit")) {
                inputBuffer.close();
                running = false;
            } else {
                System.out.printf("Unrecognized command '%s'.\n", command);
                running = true;
            }
        }
    }
}
