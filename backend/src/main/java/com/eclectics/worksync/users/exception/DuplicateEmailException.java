package com.eclectics.worksync.users.exception;

/** Friendly pre-check failure; the database still enforces uniqueness for concurrent inserts. */
public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException() {
        super("Email is already registered");
    }
}
