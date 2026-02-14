package com.tarsv2.model;

public record ModelResponse(Status status, String content, String message) {

    public static ModelResponse ok(String content) {
        return new ModelResponse(Status.OK, content, "");
    }

    public static ModelResponse notImplemented(String message) {
        return new ModelResponse(Status.NOT_IMPLEMENTED, "", message);
    }

    public static ModelResponse unavailable(String message) {
        return new ModelResponse(Status.UNAVAILABLE, "", message);
    }

    public static ModelResponse timeout(String message) {
        return new ModelResponse(Status.TIMEOUT, "", message);
    }

    public enum Status {
        OK,
        NOT_IMPLEMENTED,
        TIMEOUT,
        UNAVAILABLE,
        ERROR
    }
}
