import java.io.*;
import java.util.*;

public class Main {

    private static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH");

        if (pathEnv == null) {
            return null;
        }

        String[] directories = pathEnv.split(File.pathSeparator);

        for (String dir : directories) {
            File file = new File(dir, command);

            if (file.exists() && file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine();

            // exit
            if (input.equals("exit") || input.equals("exit 0")) {
                break;
            }

            // echo
            else if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
            }

            // type
            else if (input.startsWith("type ")) {
                String cmd = input.substring(5);

                if (cmd.equals("echo")
                        || cmd.equals("exit")
                        || cmd.equals("type")) {

                    System.out.println(cmd + " is a shell builtin");
                } else {
                    String executablePath = findExecutable(cmd);

                    if (executablePath != null) {
                        System.out.println(cmd + " is " + executablePath);
                    } else {
                        System.out.println(cmd + ": not found");
                    }
                }
            }

            // external commands
            else {
                String[] parts = input.split("\\s+");
                String command = parts[0];

                String executablePath = findExecutable(command);

                if (executablePath != null) {

                    ProcessBuilder pb = new ProcessBuilder(parts);
                    pb.redirectErrorStream(true);

                    Process process = pb.start();

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()));

                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }

                    process.waitFor();

                } else {
                    System.out.println(command + ": command not found");
                }
            }
        }

        scanner.close();
    }
}