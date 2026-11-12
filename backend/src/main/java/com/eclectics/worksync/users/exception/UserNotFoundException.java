package com.eclectics.worksync.users.exception;

/** Missing account; a future HTTP error handler can return a safe 404 response. */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() {
        super("User not found");
    }
}
