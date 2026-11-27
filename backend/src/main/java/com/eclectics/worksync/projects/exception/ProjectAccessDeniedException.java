package com.eclectics.worksync.projects.exception;

public class ProjectAccessDeniedException extends RuntimeException {
    public ProjectAccessDeniedException() { super("Project access denied"); }
}
