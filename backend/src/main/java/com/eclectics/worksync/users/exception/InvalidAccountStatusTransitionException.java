package com.eclectics.worksync.users.exception;

/** Invalid lifecycle operation; the failed operation leaves the entity status unchanged. */
public class InvalidAccountStatusTransitionException extends RuntimeException {
    public InvalidAccountStatusTransitionException() {
        super("Operation is not valid for the account's current status");
    }
}
