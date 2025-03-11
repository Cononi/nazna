package io.github.cononi.core.exception;

public class BeanRegistrationException extends RuntimeException {
    public BeanRegistrationException(String message) {
        super(message);
    }

    public BeanRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
