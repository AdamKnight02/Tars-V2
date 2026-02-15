package com.tarsv2.codex;

import com.tarsv2.codex.instruction.PatchInstruction;
import com.tarsv2.codex.instruction.PatchOperation;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class DeterministicPatchBuilder {

    public String buildUnifiedDiff(String targetFilePath, String originalContent, ChangeRequest request) throws IOException {
        String path = requirePath(targetFilePath);
        PatchOperation operation = switch ((request.action() == null ? "" : request.action().trim().toLowerCase())) {
            case "replace" -> PatchOperation.REPLACE;
            case "append" -> PatchOperation.APPEND;
            case "replace_hint" -> PatchOperation.REPLACE_HINT;
            case "insert_after_hint" -> PatchOperation.INSERT_AFTER_HINT;
            default -> throw new IllegalArgumentException("Unsupported change action: " + request.action());
        };
        PatchInstruction instruction = new PatchInstruction(path, operation, request.locationHint(), request.content());
        return buildUnifiedDiff(path, originalContent, instruction);
    }

    public String buildUnifiedDiff(String targetFilePath, String originalContent, PatchInstruction instruction) throws IOException {
        String path = requirePath(targetFilePath);
        String oldContent = Objects.requireNonNull(originalContent, "originalContent must not be null");
        PatchInstruction safeInstruction = Objects.requireNonNull(instruction, "patch instruction is required");

        String updatedContent = applyInstruction(oldContent, safeInstruction);
        if (oldContent.equals(updatedContent)) {
            return "";
        }

        return formatDiff(path, oldContent, updatedContent);
    }

    String applyInstruction(String originalContent, PatchInstruction instruction) {
        if (instruction.content() == null) {
            throw new IllegalArgumentException("Patch content is required.");
        }

        return switch (instruction.operation()) {
            case REPLACE -> instruction.content();
            case APPEND -> append(originalContent, instruction.content());
            case REPLACE_HINT -> replaceHint(originalContent, instruction.location(), instruction.content());
            case INSERT_AFTER_HINT -> insertAfterHint(originalContent, instruction.location(), instruction.content());
        };
    }

    String applyChange(String originalContent, ChangeRequest request) {
        PatchOperation operation = switch ((request.action() == null ? "" : request.action().trim().toLowerCase())) {
            case "replace" -> PatchOperation.REPLACE;
            case "append" -> PatchOperation.APPEND;
            case "replace_hint" -> PatchOperation.REPLACE_HINT;
            case "insert_after_hint" -> PatchOperation.INSERT_AFTER_HINT;
            default -> throw new IllegalArgumentException("Unsupported change action: " + request.action());
        };
        return applyInstruction(originalContent,
                new PatchInstruction("src/main/java/placeholder.java", operation, request.locationHint(), request.content()));
    }

    private String append(String original, String content) {
        if (original.endsWith("\n") || original.isEmpty()) {
            return original + content;
        }
        return original + "\n" + content;
    }

    private String replaceHint(String original, String locationHint, String content) {
        if (locationHint == null || locationHint.isBlank()) {
            throw new IllegalArgumentException("location is required for REPLACE_HINT operation.");
        }
        int idx = original.indexOf(locationHint);
        if (idx < 0) {
            throw new IllegalArgumentException("location not found for REPLACE_HINT operation.");
        }
        return original.substring(0, idx) + content + original.substring(idx + locationHint.length());
    }

    private String insertAfterHint(String original, String locationHint, String content) {
        if (locationHint == null || locationHint.isBlank()) {
            throw new IllegalArgumentException("location is required for INSERT_AFTER_HINT operation.");
        }
        int idx = original.indexOf(locationHint);
        if (idx < 0) {
            throw new IllegalArgumentException("location not found for INSERT_AFTER_HINT operation.");
        }
        int insertionPoint = idx + locationHint.length();
        return original.substring(0, insertionPoint) + content + original.substring(insertionPoint);
    }

    private String formatDiff(String path, String oldContent, String newContent) throws IOException {
        InMemoryRepository repo = new InMemoryRepository.Builder()
                .setRepositoryDescription(new DfsRepositoryDescription("deterministic-diff"))
                .build();
        try (ObjectInserter inserter = repo.newObjectInserter();
             ByteArrayOutputStream out = new ByteArrayOutputStream();
             DiffFormatter formatter = new DiffFormatter(out)) {

            ObjectId oldTree = createSingleFileTree(inserter, path, oldContent);
            ObjectId newTree = createSingleFileTree(inserter, path, newContent);
            inserter.flush();

            formatter.setRepository(repo);
            formatter.setContext(3);
            formatter.format(treeParser(repo, oldTree), treeParser(repo, newTree));
            formatter.flush();
            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate deterministic unified diff.", e);
        }
    }

    private AbstractTreeIterator treeParser(InMemoryRepository repo, ObjectId treeId) throws Exception {
        CanonicalTreeParser parser = new CanonicalTreeParser();
        try (var reader = repo.newObjectReader()) {
            parser.reset(reader, treeId);
        }
        return parser;
    }

    private ObjectId createSingleFileTree(ObjectInserter inserter, String path, String content) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        ObjectId blobId = inserter.insert(Constants.OBJ_BLOB, bytes);

        DirCache dirCache = DirCache.newInCore();
        DirCacheBuilder builder = dirCache.builder();
        DirCacheEntry entry = new DirCacheEntry(path);
        entry.setFileMode(FileMode.REGULAR_FILE);
        entry.setObjectId(blobId);
        builder.add(entry);
        builder.finish();

        return dirCache.writeTree(inserter);
    }

    private String requirePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("target file path is required");
        }
        return path;
    }
}
