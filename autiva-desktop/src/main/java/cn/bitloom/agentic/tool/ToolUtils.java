package cn.bitloom.agentic.tool;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public final class ToolUtils {

    private static final Set<String> IGNORED_DIR_NAMES = Set.of(
            ".git", "node_modules", "target", "build", ".idea",
            ".vscode", "dist", "__pycache__", ".gradle", ".mvn"
    );

    private ToolUtils() {
    }

    public static boolean isIgnoredPath(Path path) {
        for (Path part : path) {
            if (IGNORED_DIR_NAMES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    public static Path resolveWorkingDirectory(String path, Path workingDirectory) {
        if (path != null && !path.isBlank()) {
            return Paths.get(path);
        }
        if (workingDirectory != null) {
            return workingDirectory;
        }
        return Paths.get(System.getProperty("user.dir"));
    }

    public static int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

    public static String replaceFirst(String text, String oldStr, String newStr) {
        int index = text.indexOf(oldStr);
        if (index == -1) {
            return text;
        }
        return text.substring(0, index) + newStr + text.substring(index + oldStr.length());
    }

    public static String replaceAll(String text, String oldStr, String newStr) {
        StringBuilder result = new StringBuilder();
        int index = 0;
        int lastIndex = 0;

        while ((index = text.indexOf(oldStr, lastIndex)) != -1) {
            result.append(text, lastIndex, index);
            result.append(newStr);
            lastIndex = index + oldStr.length();
        }
        result.append(text.substring(lastIndex));

        return result.toString();
    }

    public static String generateEditSnippet(String fileContent, String newString) {
        String[] lines = fileContent.split("\n", -1);
        String[] newLines = newString.split("\n", -1);

        int editStartLine = -1;
        int editEndLine = -1;

        for (int i = 0; i < lines.length; i++) {
            if (newLines.length > 0 && lines[i].contains(newLines[0])) {
                boolean matches = true;
                for (int j = 1; j < newLines.length && i + j < lines.length; j++) {
                    if (!lines[i + j].contains(newLines[j])) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    editStartLine = i;
                    editEndLine = i + newLines.length - 1;
                    break;
                }
            }
        }

        if (editStartLine == -1) {
            editStartLine = 0;
            editEndLine = Math.min(10, lines.length - 1);
        }

        int contextBefore = 5;
        int contextAfter = 5;
        int startLine = Math.max(0, editStartLine - contextBefore);
        int endLine = Math.min(lines.length - 1, editEndLine + contextAfter);

        StringBuilder snippet = new StringBuilder();
        for (int i = startLine; i <= endLine; i++) {
            snippet.append(String.format("%6d→%s", i + 1, lines[i]));
            if (i < endLine) {
                snippet.append("\n");
            }
        }
        return snippet.toString();
    }
}
