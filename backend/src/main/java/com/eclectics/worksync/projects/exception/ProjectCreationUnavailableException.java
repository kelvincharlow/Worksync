package com.eclectics.worksync.projects.exception;

public class ProjectCreationUnavailableException extends RuntimeException {
    public ProjectCreationUnavailableException() { super("Project creation requires the Users PROJECT_CREATE permission implementation"); }
}
