package com.tarsv2.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class RepositoryContextInjector {

    private static final Logger log = LoggerFactory.getLogger(RepositoryContextInjector.class);
    private static final Path SOURCE_ROOT = Paths.get("src/main/java");
    private static final Path INTENT_SOURCE_ROOT = Paths.get("src/main/java/com/tarsv2/openclaw");
    private static final Pattern TYPE_PATTERN = Pattern.compile(
            "^\\s*(public|protected|private)?\\s*(abstract\\s+|final\\s+)?(class|interface|record|enum)\\s+([A-Za-z0-9_]+).*$"
    );
    private static final Pattern PUBLIC_FIELD_PATTERN = Pattern.compile(
            "^\\s*public\\s+(?!class\\b|interface\\b|enum\\b|record\\b)(?!.*\\()(.+);\\s*$"
    );
    private static final Pattern PUBLIC_METHOD_PATTERN = Pattern.compile(
            "^\\s*public\\s+[^=;{}]*\\([^;{}]*\\)\\s*(?:throws\\s+[^;{}]+)?\\s*(?:\\{|;)?\\s*$"
    );
    private static final int MAX_INJECTED_TOKENS = 2_000;

    InjectionResult injectIfRelevant(String prompt, String topic) {
        Objects.requireNonNull(prompt);
        if (!isIntentTopic(topic)) {
            return InjectionResult.notTriggered(prompt);
        }

        List<Path> sourceFiles = loadIntentSourceFiles();
        if (sourceFiles.isEmpty()) {
            String block = "REPOSITORY CONTEXT: None found.";
            String injectedPrompt = block + "\n\n" + prompt;
            return new InjectionResult(true, List.of(), injectedPrompt, block, "intent");
        }

        String extracted = buildExtractedContent(sourceFiles);
        String block = "---\nREPOSITORY CONTEXT:\n" + extracted + "\n---";
        String injectedPrompt = block + "\n\n" + prompt;
        List<String> selected = sourceFiles.stream()
                .map(path -> SOURCE_ROOT.relativize(path).toString())
                .toList();
        return new InjectionResult(true, selected, injectedPrompt, block, "intent");
    }

    private boolean isIntentTopic(String topic) {
        return topic != null && topic.toLowerCase().contains("intent");
    }

    private List<Path> loadIntentSourceFiles() {
        if (!Files.isDirectory(INTENT_SOURCE_ROOT)) {
            return List.of();
        }

        try (Stream<Path> files = Files.walk(INTENT_SOURCE_ROOT)) {
            return files
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to load Intent repository context files", e);
            return List.of();
        }
    }

    private String buildExtractedContent(List<Path> sourceFiles) {
        StringBuilder builder = new StringBuilder();
        int tokenCount = 0;
        for (Path sourceFile : sourceFiles) {
            String fileSection = extractFileSection(sourceFile);
            if (fileSection.isBlank()) {
                continue;
            }

            String nextSection = builder.length() == 0 ? fileSection : "\n\n" + fileSection;
            int nextTokens = countTokens(nextSection);
            if (tokenCount + nextTokens <= MAX_INJECTED_TOKENS) {
                builder.append(nextSection);
                tokenCount += nextTokens;
                continue;
            }

            int remaining = MAX_INJECTED_TOKENS - tokenCount;
            if (remaining > 0) {
                builder.append(trimToTokenLimit(nextSection, remaining));
            }
            break;
        }

        return builder.toString();
    }

    private int countTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private String trimToTokenLimit(String text, int tokenLimit) {
        if (text == null || text.isBlank() || tokenLimit <= 0) {
            return "";
        }

        String[] tokens = text.trim().split("\\s+");
        int end = Math.min(tokenLimit, tokens.length);
        return String.join(" ", java.util.Arrays.copyOfRange(tokens, 0, end));
    }

    private String extractFileSection(Path path) {
        String className = "Unknown";
        List<String> publicFields = new ArrayList<>();
        List<String> publicMethods = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                Matcher typeMatcher = TYPE_PATTERN.matcher(line);
                if (typeMatcher.matches()) {
                    className = typeMatcher.group(4);
                }

                Matcher fieldMatcher = PUBLIC_FIELD_PATTERN.matcher(line);
                if (fieldMatcher.matches()) {
                    publicFields.add("public " + fieldMatcher.group(1).trim() + ";");
                }

                Matcher methodMatcher = PUBLIC_METHOD_PATTERN.matcher(line);
                if (methodMatcher.matches()) {
                    String methodLine = line.trim();
                    if (methodLine.endsWith("{")) {
                        methodLine = methodLine.substring(0, methodLine.length() - 1).trim();
                    }
                    publicMethods.add(methodLine.endsWith(";") ? methodLine : methodLine + ";");
                }
            }
        } catch (IOException e) {
            log.debug("Failed reading {}", path, e);
            return "";
        }

        String relativePath = SOURCE_ROOT.relativize(path).toString();
        String fields = publicFields.isEmpty() ? "- None" : publicFields.stream().map(v -> "- " + v).collect(java.util.stream.Collectors.joining("\n"));
        String methods = publicMethods.isEmpty() ? "- None" : publicMethods.stream().map(v -> "- " + v).collect(java.util.stream.Collectors.joining("\n"));

        return "File: " + relativePath + "\n"
                + "Class: " + className + "\n"
                + "Public fields:\n" + fields + "\n"
                + "Public methods:\n" + methods;
    }

    record InjectionResult(
            boolean keywordTriggered,
            List<String> selectedFiles,
            String prompt,
            String appendedBlock,
            String triggeredKeyword
    ) {
        static InjectionResult notTriggered(String prompt) {
            return new InjectionResult(false, List.of(), prompt, "", "");
        }

        int injectedCharacters() {
            return appendedBlock.length();
        }
    }
}
