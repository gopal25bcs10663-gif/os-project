import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static String currentDirectory;
    private static final List<String> BUILTINS = Arrays.asList("echo", "exit", "pwd", "cd", "type", "jobs");

    private static class Job {
        int id;
        long pid;
        String command;
        String status;
        List<Process> processes;

        Job(int id, long pid, String command, List<Process> processes) {
            this.id = id;
            this.pid = pid;
            this.command = command;
            this.status = "Running";
            this.processes = processes;
        }

        boolean isAlive() {
            for (Process p : processes) {
                if (p.isAlive()) return true;
            }
            return false;
        }
    }

    private static final List<Job> activeJobs = new ArrayList<>();

    private static void reapBackgroundJobs(PrintStream outStream) {
        for (Job job : activeJobs) {
            if (job.status.equals("Running") && !job.isAlive()) {
                job.status = "Done";
                if (job.command.endsWith("&")) {
                    job.command = job.command.substring(0, job.command.length() - 1).trim();
                }
            }
        }

        int size = activeJobs.size();
        for (int i = 0; i < size; i++) {
            Job job = activeJobs.get(i);
            char marker = ' ';
            if (i == size - 1) {
                marker = '+';
            } else if (i == size - 2) {
                marker = '-';
            }

            String paddedStatus = String.format("%-24s", job.status);
            outStream.println("[" + job.id + "]" + marker + "  " + paddedStatus + job.command);
        }

        activeJobs.removeIf(job -> job.status.equals("Done"));
    }

    private static void reapBeforePrompt(PrintStream outStream) {
        boolean hasDoneJobs = false;
        for (Job job : activeJobs) {
            if (job.status.equals("Running") && !job.isAlive()) {
                job.status = "Done";
                if (job.command.endsWith("&")) {
                    job.command = job.command.substring(0, job.command.length() - 1).trim();
                }
                hasDoneJobs = true;
            }
        }

        if (hasDoneJobs) {
            int size = activeJobs.size();
            for (int i = 0; i < size; i++) {
                Job job = activeJobs.get(i);
                char marker = ' ';
                if (i == size - 1) {
                    marker = '+';
                } else if (i == size - 2) {
                    marker = '-';
                }

                if (job.status.equals("Done")) {
                    String paddedStatus = String.format("%-24s", job.status);
                    outStream.println("[" + job.id + "]" + marker + "  " + paddedStatus + job.command);
                }
            }
            activeJobs.removeIf(job -> job.status.equals("Done"));
        }
    }

    public static void main(String[] args) {
        currentDirectory = System.getProperty("user.dir");
        Scanner scanner = new Scanner(System.in);

        while (true) {
            reapBeforePrompt(System.out);

            System.out.print("$ ");
            System.out.flush();

            if (!scanner.hasNextLine()) {
                break;
            }

            String rawInput = scanner.nextLine();
            String input = rawInput.trim();
            if (input.isEmpty()) {
                continue;
            }

            boolean isBackground = false;
            if (input.endsWith("&")) {
                isBackground = true;
                input = input.substring(0, input.length() - 1).trim();
            }

            List<String> tokens = parseCommand(input);
            if (tokens.isEmpty()) continue;

            if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).equals("&")) {
                isBackground = true;
                tokens.remove(tokens.size() - 1);
            }

            int pipeIndex = tokens.indexOf("|");
            if (pipeIndex != -1) {
                List<String> firstCmdTokens = tokens.subList(0, pipeIndex);
                List<String> secondCmdTokens = tokens.subList(pipeIndex + 1, tokens.size());

                executePipeline(firstCmdTokens, secondCmdTokens, isBackground, rawInput.trim());
                continue;
            }

            List<String> cmdArgs = new ArrayList<>();
            String stdoutFile = null;
            String stderrFile = null;
            boolean hasStdoutRedirect = false;
            boolean isStdoutAppend = false;
            boolean hasStderrRedirect = false;
            boolean isStderrAppend = false;

            for (int i = 0; i < tokens.size(); i++) {
                if (tokens.get(i).equals("__STDOUT_REDIRECT__")) {
                    if (i + 1 < tokens.size()) {
                        stdoutFile = tokens.get(i + 1);
                        hasStdoutRedirect = true;
                        isStdoutAppend = false;
                        i++;
                    }
                } else if (tokens.get(i).equals("__STDOUT_APPEND__")) {
                    if (i + 1 < tokens.size()) {
                        stdoutFile = tokens.get(i + 1);
                        hasStdoutRedirect = true;
                        isStdoutAppend = true;
                        i++;
                    }
                } else if (tokens.get(i).equals("__STDERR_REDIRECT__")) {
                    if (i + 1 < tokens.size()) {
                        stderrFile = tokens.get(i + 1);
                        hasStderrRedirect = true;
                        isStderrAppend = false;
                        i++;
                    }
                } else if (tokens.get(i).equals("__STDERR_APPEND__")) {
                    if (i + 1 < tokens.size()) {
                        stderrFile = tokens.get(i + 1);
                        hasStderrRedirect = true;
                        isStderrAppend = true;
                        i++;
                    }
                } else {
                    cmdArgs.add(tokens.get(i));
                }
            }

            if (cmdArgs.isEmpty()) continue;

            PrintStream outStream = System.out;
            PrintStream errStream = System.err;
            File stdoutFileObj = null;
            File stderrFileObj = null;

            if (hasStdoutRedirect && stdoutFile != null) {
                stdoutFileObj = new File(stdoutFile);
                if (!stdoutFileObj.isAbsolute()) stdoutFileObj = new File(currentDirectory, stdoutFile);
                try {
                    File parent = stdoutFileObj.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    outStream = new PrintStream(new FileOutputStream(stdoutFileObj, isStdoutAppend));
                } catch (IOException e) {
                    System.err.println("Shell redirection error: " + e.getMessage());
                    continue;
                }
            }

            if (hasStderrRedirect && stderrFile != null) {
                stderrFileObj = new File(stderrFile);
                if (!stderrFileObj.isAbsolute()) stderrFileObj = new File(currentDirectory, stderrFile);
                try {
                    File parent = stderrFileObj.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    errStream = new PrintStream(new FileOutputStream(stderrFileObj, isStderrAppend));
                } catch (IOException e) {
                    System.err.println("Shell redirection error: " + e.getMessage());
                    continue;
                }
            }

            try {
                String command = cmdArgs.get(0);

                if (command.equals("exit")) {
                    System.exit(0);
                } else if (command.equals("echo")) {
                    for (int i = 1; i < cmdArgs.size(); i++) {
                        outStream.print(cmdArgs.get(i));
                        if (i < cmdArgs.size() - 1) outStream.print(" ");
                    }
                    outStream.println();
                } else if (command.equals("pwd")) {
                    outStream.println(currentDirectory);
                } else if (command.equals("jobs")) {
                    reapBackgroundJobs(outStream);
                } else if (command.equals("cd")) {
                    if (cmdArgs.size() < 2) {
                        String home = System.getenv("HOME");
                        if (home != null) currentDirectory = home;
                    } else {
                        String path = cmdArgs.get(1);
                        if (path.equals("~")) {
                            String home = System.getenv("HOME");
                            if (home != null) currentDirectory = home;
                        } else {
                            File targetDir = new File(path);
                            if (!targetDir.isAbsolute()) targetDir = new File(currentDirectory, path);
                            if (targetDir.exists() && targetDir.isDirectory()) {
                                currentDirectory = targetDir.getCanonicalPath();
                            } else {
                                errStream.println("cd: " + path + ": No such file or directory");
                            }
                        }
                    }
                } else if (command.equals("type")) {
                    if (cmdArgs.size() >= 2) {
                        String target = cmdArgs.get(1);
                        if (BUILTINS.contains(target)) {
                            outStream.println(target + " is a shell builtin");
                        } else {
                            String execPath = findInPath(target);
                            if (execPath != null) {
                                outStream.println(target + " is " + execPath);
                            } else {
                                outStream.println(target + ": not found");
                            }
                        }
                    }
                } else {
                    String execPath = findInPath(command);
                    if (execPath == null) {
                        outStream.println(command + ": not found");
                    } else {
                        ProcessBuilder pb = new ProcessBuilder(cmdArgs);
                        pb.directory(new File(currentDirectory));
                        
                        if (!hasStdoutRedirect && !hasStderrRedirect) {
                            pb.inheritIO();
                        } else {
                            if (hasStdoutRedirect) {
                                if (isStdoutAppend) pb.redirectOutput(ProcessBuilder.Redirect.appendTo(stdoutFileObj));
                                else pb.redirectOutput(stdoutFileObj);
                            } else {
                                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                            }

                            if (hasStderrRedirect) {
                                if (isStderrAppend) pb.redirectError(ProcessBuilder.Redirect.appendTo(stderrFileObj));
                                else pb.redirectError(stderrFileObj);
                            } else {
                                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                            }
                        }

                        Process process = pb.start();

                        if (isBackground) {
                            int nextJobId = 1;
                            if (!activeJobs.isEmpty()) {
                                nextJobId = activeJobs.get(activeJobs.size() - 1).id + 1;
                            }

                            System.out.println("[" + nextJobId + "] " + process.pid());
                            System.out.flush();
                            
                            activeJobs.add(new Job(nextJobId, process.pid(), rawInput.trim(), Arrays.asList(process)));
                        } else {
                            process.waitFor();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (hasStdoutRedirect && outStream != System.out && outStream != null) outStream.close();
                if (hasStderrRedirect && errStream != System.err && errStream != null) errStream.close();
            }
        }
    }

    private static void executePipeline(List<String> firstTokens, List<String> secondTokens, boolean isBackground, String originalCommand) {
        try {
            if (firstTokens.isEmpty() || secondTokens.isEmpty()) return;

            String cmd1 = firstTokens.get(0);
            String path1 = findInPath(cmd1);
            String cmd2 = secondTokens.get(0);
            String path2 = findInPath(cmd2);

            if (path1 == null) {
                System.out.println(cmd1 + ": not found");
                return;
            }
            if (path2 == null) {
                System.out.println(cmd2 + ": not found");
                return;
            }

            firstTokens.set(0, path1);
            secondTokens.set(0, path2);

            ProcessBuilder pb1 = new ProcessBuilder(firstTokens).directory(new File(currentDirectory));
            ProcessBuilder pb2 = new ProcessBuilder(secondTokens).directory(new File(currentDirectory));

            // Only redirect the final consumer's output to the console terminal
            pb2.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            pb2.redirectError(ProcessBuilder.Redirect.INHERIT);

            List<Process> pipeline = ProcessBuilder.startPipeline(Arrays.asList(pb1, pb2));

            if (isBackground) {
                int nextJobId = 1;
                if (!activeJobs.isEmpty()) {
                    nextJobId = activeJobs.get(activeJobs.size() - 1).id + 1;
                }
                
                long rootPid = pipeline.get(pipeline.size() - 1).pid();
                System.out.println("[" + nextJobId + "] " + rootPid);
                System.out.flush();

                activeJobs.add(new Job(nextJobId, rootPid, originalCommand, pipeline));
            } else {
                for (Process p : pipeline) {
                    p.waitFor();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<String> parseCommand(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inSingleQuote) {
                if (c == '\'') inSingleQuote = false;
                else currentToken.append(c);
            } else if (inDoubleQuote) {
                if (c == '\\') {
                    if (i + 1 < input.length()) {
                        char next = input.charAt(i + 1);
                        if (next == '"' || next == '\\') {
                            currentToken.append(next);
                            i++;
                        } else {
                            currentToken.append(c);
                        }
                    } else {
                        currentToken.append(c);
                    }
                } else if (c == '"') {
                    inDoubleQuote = false;
                } else {
                    currentToken.append(c);
                }
            } else {
                if (c == '\\') {
                    if (i + 1 < input.length()) {
                        currentToken.append(input.charAt(i + 1));
                        i++;
                    }
                } else if (c == '\'') {
                    inSingleQuote = true;
                } else if (c == '"') {
                    inDoubleQuote = true;
                } else if (c == '|') {
                    if (currentToken.length() > 0) {
                        tokens.add(currentToken.toString());
                        currentToken.setLength(0);
                    }
                    tokens.add("|");
                } else if (Character.isWhitespace(c)) {
                    if (currentToken.length() > 0) {
                        tokens.add(currentToken.toString());
                        currentToken.setLength(0);
                    }
                } else if (c == '1' && i + 2 < input.length() && input.charAt(i + 1) == '>' && input.charAt(i + 2) == '>') {
                    if (currentToken.length() > 0) {
                        tokens.add(currentToken.toString());
                        currentToken.setLength(0);
                    }
                    tokens.add("__STDOUT_APPEND__");
                    i += 2;
                } else if (c == '1' && i + 1 < input.length() && input.charAt(i + 1) == '>') {
                    if (currentToken.length() > 0) {
                        tokens.add(currentToken.toString());
                        currentToken.setLength(0);
                    }
                    tokens.add("__STDOUT_REDIRECT__");
                    i++;
                } else if (c == '2' && i + 2 < input.length() && input.charAt(i + 1) == '>' && input.charAt(i + 2) == '>') {
                    if (currentToken.length() > 0) {
                        tokens.add(currentToken.toString());
                        currentToken.setLength(0);
                    }
                    tokens.add("__STDERR_APPEND__");
                    i += 2;
                } else if (c == '2' && i + 1 < input.length() && input.charAt(i + 1) == '>') {
                    if (currentToken.length() > 0) {
                        tokens.add(currentToken.toString());
                        currentToken.setLength(0);
                    }
                    tokens.add("__STDERR_REDIRECT__");
                    i++;
                } else if (c == '>' && i + 1 < input.length() && input.charAt(i + 1) == '>') {
                    if (currentToken.length() > 0) {
                        tokens.add(currentToken.toString());
                        currentToken.setLength(0);
                    }
                    tokens.add("__STDOUT_APPEND__");
                    i++;
                } else if (c == '>') {
                    if (currentToken.length() > 0) {
                        tokens.add(currentToken.toString());
                        currentToken.setLength(0);
                    }
                    tokens.add("__STDOUT_REDIRECT__");
                } else {
                    currentToken.append(c);
                }
            }
        }
        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }
        return tokens;
    }

    private static String findInPath(String command) {
        if (command.contains("/")) {
            File f = new File(command);
            if (f.exists() && f.isFile() && f.canExecute()) {
                return f.getAbsolutePath();
            }
            return null;
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        String[] dirs = pathEnv.split(File.pathSeparator);
        for (String dir : dirs) {
            File file = new File(dir, command);
            if (file.exists() && file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }
}