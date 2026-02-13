package com.tarsv2.codex;

import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class DeterministicPatchBuilder {

    public String buildUnifiedDiff(String targetFilePath, String originalContent, ChangeRequest request) {
        String path = requirePath(targetFilePath);
        String oldContent = Objects.requireNonNull(originalContent, "originalContent must not be null");
        ChangeRequest safeRequest = Objects.requireNonNull(request, "change request is required");

        String updatedContent = applyChange(oldContent, safeRequest);
        if (oldContent.equals(updatedContent)) {
            return "";
        }

        return formatDiff(path, oldContent, updatedContent);
    }

    String applyChange(String originalContent, ChangeRequest request) {
        String action = request.action() == null ? "" : request.action().trim().toLowerCase();
        String content = request.content();

        if (content == null) {
            throw new IllegalArgumentException("Change content is required.");
        }

        return switch (action) {
            case "replace" -> content;
            case "append" -> append(originalContent, content);
            case "replace_hint" -> replaceHint(originalContent, request.locationHint(), content);
            case "insert_after_hint" -> insertAfterHint(originalContent, request.locationHint(), content);
            default -> throw new IllegalArgumentException("Unsupported change action: " + request.action());
        };
    }

    private String append(String original, String content) {
        if (original.endsWith("\n") || original.isEmpty()) {
            return original + content;
        }
        return original + "\n" + content;
    }

    private String replaceHint(String original, String locationHint, String content) {
        if (locationHint == null || locationHint.isBlank()) {
            throw new IllegalArgumentException("locationHint is required for replace_hint action.");
        }
        int idx = original.indexOf(locationHint);
        if (idx < 0) {
            throw new IllegalArgumentException("locationHint not found for replace_hint action.");
        }
        return original.substring(0, idx) + content + original.substring(idx + locationHint.length());
    }

    private String insertAfterHint(String original, String locationHint, String content) {
        if (locationHint == null || locationHint.isBlank()) {
            throw new IllegalArgumentException("locationHint is required for insert_after_hint action.");
        }
        int idx = original.indexOf(locationHint);
        if (idx < 0) {
            throw new IllegalArgumentException("locationHint not found for insert_after_hint action.");
        }
        int insertionPoint = idx + locationHint.length();
        return original.substring(0, insertionPoint) + content + original.substring(insertionPoint);
    }

    private String formatDiff(String path, String oldContent, String newContent) {
        InMemoryRepository repo = new InMemoryRepository.Builder().setRepositoryDescription("deterministic-diff").build();
        try (ObjectInserter inserter = repo.newObjectInserter();
             ByteArrayOutputStream out = new ByteArrayOutputStream();
             DiffFormatter formatter = new DiffFormatter(out)) {

            ObjectId oldTree = createSingleFileTree(inserter, path, oldContent);
            ObjectId newTree = createSingleFileTree(inserter, path, newContent);
            inserter.flush();

            formatter.setRepository(repo);
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
