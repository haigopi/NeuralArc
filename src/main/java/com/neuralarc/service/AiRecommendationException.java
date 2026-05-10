package com.neuralarc.service;

public class AiRecommendationException extends Exception {
    public AiRecommendationException(String message) {
        super(message);
    }

    public AiRecommendationException(String message, Throwable cause) {
        super(message, cause);
    }
}
