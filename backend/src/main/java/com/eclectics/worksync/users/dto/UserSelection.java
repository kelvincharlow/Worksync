package com.eclectics.worksync.users.dto;

import java.util.UUID;

/** Minimal trusted user-selection data; no credentials, phone, roles or account controls. */
public record UserSelection(UUID id, String firstName, String lastName, String email) {
    public String displayName() { return firstName + " " + lastName; }
}
