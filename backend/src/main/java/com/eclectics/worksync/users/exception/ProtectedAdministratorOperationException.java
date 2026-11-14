package com.eclectics.worksync.users.exception;

/** The operation would violate an administrator account safety rule. */
public class ProtectedAdministratorOperationException extends RuntimeException {
    public ProtectedAdministratorOperationException() {
        super("Administrator account operation is protected");
    }
}
