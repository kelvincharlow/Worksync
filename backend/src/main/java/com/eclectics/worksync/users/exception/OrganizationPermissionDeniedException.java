package com.eclectics.worksync.users.exception;

public class OrganizationPermissionDeniedException extends RuntimeException {
    public OrganizationPermissionDeniedException() { super("Active account and organization permission required"); }
}
