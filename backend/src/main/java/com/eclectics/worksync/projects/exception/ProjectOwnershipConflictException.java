package com.eclectics.worksync.projects.exception;
public class ProjectOwnershipConflictException extends RuntimeException {
    public ProjectOwnershipConflictException() { super("Ownership transfer conflicts with current eligibility"); }
}
