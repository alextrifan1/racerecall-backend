package com.racerecall.backend.exceptions;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(int sessionKey) {
        super("No session found with key " + sessionKey);
    }
}
