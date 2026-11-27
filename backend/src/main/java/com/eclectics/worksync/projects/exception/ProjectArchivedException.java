package com.eclectics.worksync.projects.exception;

public class ProjectArchivedException extends RuntimeException {
    public ProjectArchivedException() { super("Archived projects cannot be changed"); }
}
