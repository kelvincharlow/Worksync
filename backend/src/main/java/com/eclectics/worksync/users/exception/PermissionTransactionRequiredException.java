package com.eclectics.worksync.users.exception;

public class PermissionTransactionRequiredException extends RuntimeException {
    public PermissionTransactionRequiredException() { super("Permission authorization requires a writable READ_COMMITTED transaction"); }
}
