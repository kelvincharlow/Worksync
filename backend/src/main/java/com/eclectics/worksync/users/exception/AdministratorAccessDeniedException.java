package com.eclectics.worksync.users.exception;

/** Current database state no longer permits the acting administrator to make changes. */
public class AdministratorAccessDeniedException extends RuntimeException {
    public AdministratorAccessDeniedException() {
        super("Administrator access is required");
    }
}
