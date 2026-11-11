package com.eclectics.worksync.users.entity;

/** Account lifecycle state; independent of system roles and future project memberships. */
public enum AccountStatus {
    PENDING_APPROVAL,
    ACTIVE,
    REJECTED,
    BLOCKED
}
