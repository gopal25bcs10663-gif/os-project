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

    private static List<String> parseCommand(String input) {

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;

        for (int i = 0; i < input.length(); i++) {

            char ch = input.charAt(i);

            // Backslash escaping outside quotes
            if (ch == '\\' && !inSingleQuotes && !inDoubleQuotes) {

                if (i + 1 < input.length()) {
                    current.append(input.charAt(i + 1));
                    i++;
                }

                continue;
            }

            // Single quotes
            if (ch == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                continue;
            }

            // Double quotes
            if (ch == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                continue;
            }

            // Argument separator
            if (Character.isWhitespace(ch)
                    && !inSingleQuotes
                    && !inDoubleQuotes) {

                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }

                continue;
            }

            current.append(ch);
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    public static void main(String[] args) throws Exception {

        Scanner scanner = new Scanner(System.in);

        String currentDirectory =
                System.getProperty("user.dir");

        while (true) {

            System.out.print("$ ");
            System.out.flush();

            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine();

            if (input.isEmpty()) {
                continue;
            }

            List<String> tokens = parseCommand(input);

            if (tokens.isEmpty()) {
                continue;
            }

            String command = tokens.get(0);

            // exit
            if (command.equals("exit")) {
                break;
            }

            // echo
            else if (command.equals("echo")) {

                for (int i = 1; i < tokens.size(); i++) {

                    if (i > 1) {
                        System.out.print(" ");
                    }

                    System.out.print(tokens.get(i));
                }

                System.out.println();
            }

            // pwd
            else if (command.equals("pwd")) {
                System.out.println(currentDirectory);
            }

            // cd
            else if (command.equals("cd")) {

                if (tokens.size() < 2) {
                    continue;
                }

                String targetDir = tokens.get(1);

                File dir;

                if (targetDir.equals("~")) {

                    dir = new File(System.getenv("HOME"));

                } else if (targetDir.startsWith("/")) {

                    dir = new File(targetDir);

                } else {

                    dir = new File(currentDirectory, targetDir);
                }

                try {

                    File canonicalDir =
                            dir.getCanonicalFile();

                    if (canonicalDir.exists()
                            && canonicalDir.isDirectory()) {

                        currentDirectory =
                                canonicalDir.getAbsolutePath();

                    } else {

                        System.out.println(
                                "cd: "
                                        + targetDir
                                        + ": No such file or directory");
                    }

                } catch (IOException e) {

                    System.out.println(
                            "cd: "
                                    + targetDir
                                    + ": No such file or directory");
                }
            }

            // type
            else if (command.equals("type")) {

                if (tokens.size() < 2) {
                    continue;
                }

                String cmd = tokens.get(1);

                if (cmd.equals("echo")
                        || cmd.equals("exit")
                        || cmd.equals("type")
                        || cmd.equals("pwd")
                        || cmd.equals("cd")) {

                    System.out.println(
                            cmd + " is a shell builtin");

                } else {

                    String executablePath =
                            findExecutable(cmd);

                    if (executablePath != null) {

                        System.out.println(
                                cmd + " is "
                                        + executablePath);

                    } else {

                        System.out.println(
                                cmd + ": not found");
                    }
                }
            }

            // external commands
            else {

                String executablePath =
                        findExecutable(command);

                if (executablePath != null) {

                    ProcessBuilder pb =
                            new ProcessBuilder(tokens);

                    pb.directory(
                            new File(currentDirectory));

                    pb.redirectErrorStream(true);

                    Process process = pb.start();

                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            process.getInputStream()));

                    String line;

                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }

                    process.waitFor();

                } else {

                    System.out.println(
                            command + ": command not found");
                }
            }
        }

        scanner.close();
    }
}