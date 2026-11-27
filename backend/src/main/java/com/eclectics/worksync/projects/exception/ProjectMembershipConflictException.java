package com.eclectics.worksync.projects.exception;
public class ProjectMembershipConflictException extends RuntimeException {
    public ProjectMembershipConflictException() { super("Project membership conflicts with current eligibility"); }
}
