package com.tarsv2.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryContextInjectorTest {

    @Test
    void injectsRepositoryContextForResearchKeywords() {
        RepositoryContextInjector injector = new RepositoryContextInjector();

        RepositoryContextInjector.InjectionResult result = injector.inject("Please refactor the orchestrator architecture");

        assertTrue(result.augmentedPrompt().contains("REPOSITORY_CONTEXT"));
        assertFalse(result.referencedFiles().isEmpty());
    }

    @Test
    void doesNotInjectWhenKeywordAbsent() {
        RepositoryContextInjector injector = new RepositoryContextInjector();

        RepositoryContextInjector.InjectionResult result = injector.inject("hello there");

        assertEquals("hello there", result.augmentedPrompt());
        assertTrue(result.referencedFiles().isEmpty());
    }
}
