package com.tarsv2.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class RepositoryContextInjector {

    private static final Logger log = LoggerFactory.getLogger(RepositoryContextInjector.class);
    private static final Path SOURCE_ROOT = Paths.get("src/main/java/com/tarsv2");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;");
    private static final Pattern TYPE_PATTERN = Pattern.compile(
            "^\\s*(public|protected|private)?\\s*(abstract\\s+|final\\s+)?(class|interface|record|enum)\\s+([A-Za-z0-9_]+).*$"
    );
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "^\\s*(public|protected|private)\\s+([^=;]+?)\\s+([A-Za-z0-9_]+)\\s*\\([^;]*\\)\\s*(\\{|throws\\s+[^;{]+\\{|default\\s+[^;]+;|;)\\s*$"
    );
    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "^\\s*(public|protected|private)\\s+(static\\s+)?(final\\s+)?[A-Za-z0-9_<>\\[\\], ?]+\\s+[A-Za-z0-9_]+\\s*(=.+)?;\\s*$"
    );

    private static final int MAX_CONTEXT_TOKENS = 2000;
    private static final Set<String> SUPPORTED_KEYWORDS = Set.of("intent", "environment", "agent", "sandbox", "proposal");
    private static final Map<String, String> KEYWORD_PATH_HINTS = Map.of(
            "intent", "/openclaw/",
            "environment", "/environment/",
            "agent", "/agent/",
            "sandbox", "/sandbox/",
            "proposal", "/proposal"
    );

    private static volatile List<ClassStructure> cachedStructures;

    String injectIfRelevant(String prompt, String topic) {
        Objects.requireNonNull(prompt);
        if (topic == null || topic.isBlank()) {
            return prompt;
        }

        Set<String> topicKeywords = detectKeywords(topic);
        if (topicKeywords.isEmpty()) {
            return prompt;
        }

        List<ClassStructure> matches = findMatches(topic, topicKeywords);
        if (matches.isEmpty()) {
            return prompt;
        }

        String extracted = matches.stream()
                .map(this::renderStructure)
                .collect(Collectors.joining("\n\n"));

        String bounded = enforceTokenLimit(extracted);
        return prompt + "\n\n---\nREPOSITORY CONTEXT:\n" + bounded + "\n---";
    }

    private Set<String> detectKeywords(String topic) {
        String lowered = topic.toLowerCase(Locale.ROOT);
        Set<String> detected = new LinkedHashSet<>();
        for (String keyword : SUPPORTED_KEYWORDS) {
            if (lowered.contains(keyword)) {
                detected.add(keyword);
            }
        }
        return detected;
    }

    private List<ClassStructure> findMatches(String topic, Set<String> topicKeywords) {
        Set<String> tokens = Stream.of(topic.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+"))
                .filter(token -> token.length() >= 3)
                .collect(Collectors.toSet());

        return loadStructures().stream()
                .filter(structure -> matchesKeywordHints(structure.pathHint(), topicKeywords)
                        || topicKeywords.stream().anyMatch(structure.searchable()::contains)
                        || tokens.stream().anyMatch(structure.searchable()::contains))
                .sorted(Comparator.comparing(ClassStructure::fqcn))
                .limit(8)
                .toList();
    }

    private boolean matchesKeywordHints(String pathHint, Set<String> topicKeywords) {
        for (String keyword : topicKeywords) {
            String hint = KEYWORD_PATH_HINTS.get(keyword);
            if (hint != null && pathHint.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private String renderStructure(ClassStructure structure) {
        StringBuilder builder = new StringBuilder();
        builder.append("Class: ").append(structure.fqcn()).append("\n");
        builder.append("Type: ").append(structure.typeSignature()).append("\n");

        if (!structure.fields().isEmpty()) {
            builder.append("Fields:\n");
            structure.fields().forEach(field -> builder.append("  - ").append(field).append("\n"));
        }

        if (!structure.methods().isEmpty()) {
            builder.append("Methods:\n");
            structure.methods().forEach(method -> builder.append("  - ").append(method).append("\n"));
        }

        return builder.toString().trim();
    }

    private String enforceTokenLimit(String context) {
        String[] tokens = context.split("\\s+");
        if (tokens.length <= MAX_CONTEXT_TOKENS) {
            return context;
        }

        String truncated = String.join(" ", java.util.Arrays.copyOf(tokens, MAX_CONTEXT_TOKENS));
        return truncated + "\n[Repository context truncated to 2000 tokens.]";
    }

    private List<ClassStructure> loadStructures() {
        List<ClassStructure> local = cachedStructures;
        if (local != null) {
            return local;
        }

        synchronized (RepositoryContextInjector.class) {
            if (cachedStructures != null) {
                return cachedStructures;
            }

            List<ClassStructure> loaded = new ArrayList<>();
            if (!Files.isDirectory(SOURCE_ROOT)) {
                cachedStructures = List.of();
                return cachedStructures;
            }

            try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> parseClassStructure(path).ifPresent(loaded::add));
            } catch (IOException e) {
                log.warn("Failed to load repository class structures", e);
            }

            cachedStructures = List.copyOf(loaded);
            return cachedStructures;
        }
    }

    private java.util.Optional<ClassStructure> parseClassStructure(Path path) {
        String packageName = "";
        String typeSignature = "";
        String typeName = "";
        List<String> fields = new ArrayList<>();
        List<String> methods = new ArrayList<>();

        try {
            List<String> lines = Files.readAllLines(path);
            for (String raw : lines) {
                String line = raw.trim();
                if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*") || line.isEmpty()) {
                    continue;
                }

                Matcher packageMatcher = PACKAGE_PATTERN.matcher(line);
                if (packageMatcher.matches()) {
                    packageName = packageMatcher.group(1);
                    continue;
                }

                Matcher typeMatcher = TYPE_PATTERN.matcher(line);
                if (typeMatcher.matches() && typeName.isBlank()) {
                    typeName = typeMatcher.group(4);
                    typeSignature = line;
                    continue;
                }

                if (FIELD_PATTERN.matcher(line).matches()) {
                    fields.add(line);
                    continue;
                }

                if (METHOD_PATTERN.matcher(line).matches()) {
                    methods.add(line);
                }
            }
        } catch (IOException e) {
            log.debug("Failed reading {}", path, e);
            return java.util.Optional.empty();
        }

        if (typeName.isBlank()) {
            return java.util.Optional.empty();
        }

        String fqcn = packageName.isBlank() ? typeName : packageName + "." + typeName;
        String searchable = (fqcn + " " + typeSignature + " " + String.join(" ", fields) + " " + String.join(" ", methods))
                .toLowerCase(Locale.ROOT);

        return java.util.Optional.of(new ClassStructure(
                fqcn,
                typeSignature,
                List.copyOf(fields),
                List.copyOf(methods),
                path.toString().replace('\\', '/').toLowerCase(Locale.ROOT),
                searchable
        ));
    }

    private record ClassStructure(
            String fqcn,
            String typeSignature,
            List<String> fields,
            List<String> methods,
            String pathHint,
            String searchable
    ) {}
}
