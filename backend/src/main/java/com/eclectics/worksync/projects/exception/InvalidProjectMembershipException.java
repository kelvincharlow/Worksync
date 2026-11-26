package com.eclectics.worksync.projects.exception;

public class InvalidProjectMembershipException extends RuntimeException {
    public InvalidProjectMembershipException() { super("Invalid project membership or participant"); }
}
