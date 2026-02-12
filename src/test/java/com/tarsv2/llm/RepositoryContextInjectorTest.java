package com.tarsv2.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryContextInjectorTest {

    @Test
    void injectsRepositoryContextForIntentKeywordCaseInsensitive() {
        RepositoryContextInjector injector = new RepositoryContextInjector();

        RepositoryContextInjector.InjectionResult result = injector.injectIfRelevant("Prompt", "Explain INTENT flows");

        assertTrue(result.keywordTriggered());
        assertTrue(result.prompt().contains("REPOSITORY CONTEXT:"));
        assertTrue(result.prompt().contains("src/main/java/com/tarsv2/openclaw")
                        || result.prompt().contains("com/tarsv2/openclaw"),
                "Expected injected prompt to include openclaw file paths");
        assertTrue(result.injectedCharacters() > 0);
    }

    @Test
    void doesNotInjectWhenKeywordAbsent() {
        RepositoryContextInjector injector = new RepositoryContextInjector();

        RepositoryContextInjector.InjectionResult result = injector.injectIfRelevant("Prompt", "Explain sandbox behavior");

        assertFalse(result.keywordTriggered());
        assertEquals("Prompt", result.prompt());
        assertEquals(0, result.injectedCharacters());
    }
}
