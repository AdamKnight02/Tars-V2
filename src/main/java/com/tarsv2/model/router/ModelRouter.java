package com.tarsv2.model.router;

import com.tarsv2.model.ModelRequest;
import com.tarsv2.model.ModelResponse;

public interface ModelRouter {
    ModelResponse route(RoutingMode mode, ModelRequest request);
}
