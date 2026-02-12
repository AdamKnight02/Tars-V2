package com.tarsv2.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class RepositoryContextInjector {

    private static final Logger log = LoggerFactory.getLogger(RepositoryContextInjector.class);
    private static final List<String> KEYWORDS = List.of("modify", "refactor", "architecture", "intent system", "orchestrator");
    private static final List<Path> SOURCE_PATHS = List.of(
            Paths.get("src/main/java/com/tarsv2/openclaw"),
            Paths.get("src/main/java/com/tarsv2/llm"),
            Paths.get("src/main/java/com/tarsv2/environment")
    );
    private static final Pattern TYPE_PATTERN = Pattern.compile("^\\s*(?:public\\s+)?(?:abstract\\s+|final\\s+)?(?:class|interface|record|enum)\\s+([A-Za-z0-9_]+).*$");
    private static final Pattern METHOD_PATTERN = Pattern.compile("^\\s*public\\s+[^=;{}]*\\([^;{}]*\\)\\s*(?:throws\\s+[^;{}]+)?\\s*(?:\\{|;)?\\s*$");

    public InjectionResult inject(String userPrompt) {
        String prompt = userPrompt == null ? "" : userPrompt;
        if (!isResearchPrompt(prompt)) {
            return new InjectionResult(prompt, List.of());
        }

        List<FileSignature> signatures = collectSignatures();
        StringBuilder context = new StringBuilder("REPOSITORY_CONTEXT\n");
        for (FileSignature signature : signatures) {
            context.append("File: ").append(signature.path()).append("\n");
            context.append("Class: ").append(signature.className()).append("\n");
            context.append("Public methods:\n");
            if (signature.publicMethods().isEmpty()) {
                context.append("- None\n");
            } else {
                signature.publicMethods().forEach(method -> context.append("- ").append(method).append("\n"));
            }
            context.append("\n");
        }

        List<String> referenced = signatures.stream().map(FileSignature::path).toList();
        return new InjectionResult(context + "\n" + prompt, referenced);
    }

    private boolean isResearchPrompt(String prompt) {
        String lowered = prompt.toLowerCase();
        return KEYWORDS.stream().anyMatch(lowered::contains);
    }

    private List<FileSignature> collectSignatures() {
        Set<FileSignature> signatures = new LinkedHashSet<>();
        for (Path sourcePath : SOURCE_PATHS) {
            if (!Files.isDirectory(sourcePath)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(sourcePath)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .sorted()
                        .map(this::parseSignature)
                        .forEach(signatures::add);
            } catch (IOException e) {
                log.debug("Unable to read source path {}", sourcePath, e);
            }
        }
        return new ArrayList<>(signatures);
    }

    private FileSignature parseSignature(Path path) {
        String className = "Unknown";
        List<String> methods = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(path)) {
                Matcher typeMatcher = TYPE_PATTERN.matcher(line);
                if (typeMatcher.matches()) {
                    className = typeMatcher.group(1);
                }
                Matcher methodMatcher = METHOD_PATTERN.matcher(line);
                if (methodMatcher.matches()) {
                    methods.add(line.trim().replace("{", "").trim());
                }
            }
        } catch (IOException e) {
            log.debug("Unable to parse {}", path, e);
        }
        return new FileSignature(path.toString().replace('\\', '/'), className, List.copyOf(methods));
    }

    public record InjectionResult(String augmentedPrompt, List<String> referencedFiles) {}

    private record FileSignature(String path, String className, List<String> publicMethods) {}
}
