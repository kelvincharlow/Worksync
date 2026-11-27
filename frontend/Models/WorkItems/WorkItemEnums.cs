namespace Worksync.Frontend.Models.WorkItems;

public enum WorkItemType
{
    EPIC,
    STORY,
    TASK,
    BUG
}

public enum WorkItemStatus
{
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    DONE
}

public enum WorkItemPriority
{
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}

public enum BugSeverity
{
    MINOR,
    MAJOR,
    CRITICAL,
    BLOCKER
}

public enum BugStatus
{
    REPORTED,
    TRIAGE,
    ASSIGNED,
    IN_PROGRESS,
    READY_FOR_VERIFICATION,
    VERIFIED,
    CLOSED,
    REOPENED,
    INVALID,
    DUPLICATE
}

public enum VerificationOutcome
{
    PASS,
    FAIL
}