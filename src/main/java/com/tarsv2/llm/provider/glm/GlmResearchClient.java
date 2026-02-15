package com.tarsv2.llm.provider.glm;

import com.tarsv2.llm.GlmClient;
import com.tarsv2.model.ModelRequest;
import com.tarsv2.model.ModelResponse;
import com.tarsv2.model.TarsModel;

public final class GlmResearchClient implements TarsModel {

    private final GlmClient delegate;

    public GlmResearchClient() {
        this.delegate = new GlmClient();
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        String output = delegate.chat(request.userPrompt());
        if (output == null || output.startsWith("[NOT_IMPLEMENTED]")) {
            return ModelResponse.notImplemented(output == null ? "GLM unavailable" : output);
        }
        return ModelResponse.ok(output);
    }
}
