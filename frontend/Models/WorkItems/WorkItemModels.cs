namespace Worksync.Frontend.Models.WorkItems;

public class WorkItemRecord
{
    public string Id { get; set; } = string.Empty;
    public string ProjectId { get; set; } = string.Empty;
    public WorkItemType Type { get; set; } = WorkItemType.TASK;
    public string Title { get; set; } = string.Empty;
    public string? Description { get; set; }
    public WorkItemStatus Status { get; set; } = WorkItemStatus.TODO;
    public WorkItemPriority Priority { get; set; } = WorkItemPriority.MEDIUM;
    public string CreatorId { get; set; } = string.Empty;
    public string? AssigneeId { get; set; }
    public string? EpicId { get; set; }
    public string? StoryId { get; set; }
    public string? MilestoneId { get; set; }
    public DateOnly? DueDate { get; set; }
    public List<SubtaskItem> Subtasks { get; set; } = new();
    public BugDetailsRecord? BugDetails { get; set; }
    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset UpdatedAt { get; set; }
}

public class SubtaskItem
{
    public string Id { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public bool IsDone { get; set; }
}

public class BugDetailsRecord
{
    public string WorkItemId { get; set; } = string.Empty;
    public BugSeverity Severity { get; set; } = BugSeverity.MAJOR;
    public string AffectedArea { get; set; } = string.Empty;
    public string StepsToReproduce { get; set; } = string.Empty;
    public string ExpectedResult { get; set; } = string.Empty;
    public string ActualResult { get; set; } = string.Empty;
    public string ReporterId { get; set; } = string.Empty;
    public string? FixerId { get; set; }
    public string? VerifierId { get; set; }
    public BugStatus BugStatus { get; set; } = BugStatus.REPORTED;
    public string? TriageReason { get; set; }
    public List<BugVerificationRecord> Verifications { get; set; } = new();
    public List<WorkItemHistoryRecord> History { get; set; } = new();
}

public class BugVerificationRecord
{
    public string Id { get; set; } = string.Empty;
    public string VerifierId { get; set; } = string.Empty;
    public string VerifierName { get; set; } = string.Empty;
    public VerificationOutcome Outcome { get; set; }
    public string? Notes { get; set; }
    public DateTimeOffset VerifiedAt { get; set; }
}

public class WorkItemHistoryRecord
{
    public string Id { get; set; } = string.Empty;
    public string ActorId { get; set; } = string.Empty;
    public string ActorName { get; set; } = string.Empty;
    public string Action { get; set; } = string.Empty;
    public string? Note { get; set; }
    public DateTimeOffset OccurredAt { get; set; }
}

public class WorkItemSummary
{
    public int TotalWork { get; set; }
    public int TodoCount { get; set; }
    public int InProgressCount { get; set; }
    public int InReviewCount { get; set; }
    public int DoneCount { get; set; }
    public int OpenBugCount { get; set; }
    public int TriageQueueCount { get; set; }
}

public class ClientDeliverySummary
{
    public string ProjectId { get; set; } = string.Empty;
    public string ProjectName { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public string Status { get; set; } = "ACTIVE";
    public DateOnly? DueDate { get; set; }
    public int CompletedItems { get; set; }
    public int TotalItems { get; set; }
    public int MilestoneCount { get; set; }
}