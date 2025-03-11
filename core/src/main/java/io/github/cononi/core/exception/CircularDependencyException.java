package io.github.cononi.core.exception;

public class CircularDependencyException extends BeanRegistrationException {
    public CircularDependencyException(String message) {
        super(message);
    }
}