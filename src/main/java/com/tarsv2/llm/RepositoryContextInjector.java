package com.tarsv2.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class RepositoryContextInjector {

    private static final Logger log = LoggerFactory.getLogger(RepositoryContextInjector.class);
    private static final Path SOURCE_ROOT = Paths.get("src/main/java");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;");
    private static final Pattern TYPE_PATTERN = Pattern.compile(
            "^\\s*(public|protected|private)?\\s*(abstract\\s+|final\\s+)?(class|interface|record|enum)\\s+([A-Za-z0-9_]+).*$"
    );

    private static volatile List<ClassSignature> cachedSignatures;

    String injectIfRelevant(String prompt, String topic) {
        Objects.requireNonNull(prompt);
        if (topic == null || topic.isBlank()) {
            return prompt;
        }

        List<ClassSignature> matches = findMatches(topic);
        if (matches.isEmpty()) {
            return prompt;
        }

        String context = matches.stream()
                .map(sig -> "- " + sig.fqcn() + " :: " + sig.signature())
                .collect(Collectors.joining("\n"));

        return prompt + "\n\nRepository context (matched class signatures):\n" + context;
    }

    private List<ClassSignature> findMatches(String topic) {
        Set<String> tokens = Stream.of(topic.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+"))
                .filter(token -> token.length() >= 3)
                .collect(Collectors.toSet());
        if (tokens.isEmpty()) {
            return List.of();
        }

        return loadSignatures().stream()
                .filter(sig -> tokens.stream().anyMatch(sig.searchable()::contains))
                .sorted(Comparator.comparing(ClassSignature::fqcn))
                .limit(8)
                .toList();
    }

    private List<ClassSignature> loadSignatures() {
        List<ClassSignature> local = cachedSignatures;
        if (local != null) {
            return local;
        }

        synchronized (RepositoryContextInjector.class) {
            if (cachedSignatures != null) {
                return cachedSignatures;
            }

            List<ClassSignature> loaded = new ArrayList<>();
            if (!Files.isDirectory(SOURCE_ROOT)) {
                cachedSignatures = List.of();
                return cachedSignatures;
            }

            try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> parseClassSignature(path).ifPresent(loaded::add));
            } catch (IOException e) {
                log.warn("Failed to load repository class signatures", e);
            }

            cachedSignatures = List.copyOf(loaded);
            return cachedSignatures;
        }
    }

    private java.util.Optional<ClassSignature> parseClassSignature(Path path) {
        String packageName = "";
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                Matcher packageMatcher = PACKAGE_PATTERN.matcher(line);
                if (packageMatcher.matches()) {
                    packageName = packageMatcher.group(1);
                    continue;
                }

                Matcher typeMatcher = TYPE_PATTERN.matcher(line);
                if (typeMatcher.matches()) {
                    String typeName = typeMatcher.group(4);
                    String signature = line.trim();
                    String fqcn = packageName.isBlank() ? typeName : packageName + "." + typeName;
                    String searchable = (fqcn + " " + signature).toLowerCase(Locale.ROOT);
                    return java.util.Optional.of(new ClassSignature(fqcn, signature, searchable));
                }
            }
        } catch (IOException e) {
            log.debug("Failed reading {}", path, e);
        }
        return java.util.Optional.empty();
    }

    private record ClassSignature(String fqcn, String signature, String searchable) {}
}
