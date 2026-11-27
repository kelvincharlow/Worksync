package com.eclectics.worksync.users.exception;

public class InvalidPermissionTargetException extends RuntimeException {
    public InvalidPermissionTargetException() { super("Only ACTIVE users may receive permission grants"); }
}
