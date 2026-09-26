package com.neuralarc.agent;

/**
 * An argument the model sent that the tool cannot use. Its message is written for the model: it says
 * what was wrong and what the tool accepts, so the next attempt can be right.
 */
public class ToolArgumentException extends Exception {
    public ToolArgumentException(String message) {
        super(message);
    }
}
