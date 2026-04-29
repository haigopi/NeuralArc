package com.neuralarc.service;

/**
 * Thrown when the Auto Analyze service cannot complete an analysis.
 */
public class AutoAnalyzeException extends Exception {

    public AutoAnalyzeException(String message) {
        super(message);
    }

    public AutoAnalyzeException(String message, Throwable cause) {
        super(message, cause);
    }
}

