package io.jettra.server.autentification.exception;

/**
 * Thrown when an operation violates account immutability safeguards,
 * such as attempting to revoke or delete the root 'admin' user,
 * or attempting to modify the 'admin' user from an unauthorized session.
 */
public class ImmutableAccountException extends RuntimeException {

    public ImmutableAccountException(String message) {
        super(message);
    }

    public ImmutableAccountException(String message, Throwable cause) {
        super(message, cause);
    }
}
