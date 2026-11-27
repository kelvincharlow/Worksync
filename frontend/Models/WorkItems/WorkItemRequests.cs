using System.ComponentModel.DataAnnotations;

namespace Worksync.Frontend.Models.WorkItems;

public class CreateWorkItemRequest
{
    public WorkItemType Type { get; set; } = WorkItemType.TASK;

    [Required, StringLength(180, MinimumLength = 1)]
    public string Title { get; set; } = string.Empty;

    [StringLength(2000)]
    public string? Description { get; set; }

    public WorkItemPriority Priority { get; set; } = WorkItemPriority.MEDIUM;
    public string? AssigneeId { get; set; }
    public string? EpicId { get; set; }
    public string? StoryId { get; set; }
    public string? MilestoneId { get; set; }
    public DateOnly? DueDate { get; set; }
    public List<string> SubtaskTitles { get; set; } = new();
}

public class ReportBugRequest
{
    [Required, StringLength(180, MinimumLength = 1)]
    public string Title { get; set; } = string.Empty;

    public BugSeverity Severity { get; set; } = BugSeverity.MAJOR;
    public WorkItemPriority Priority { get; set; } = WorkItemPriority.MEDIUM;

    [Required, StringLength(150)]
    public string AffectedArea { get; set; } = string.Empty;

    [Required, StringLength(3000)]
    public string StepsToReproduce { get; set; } = string.Empty;

    [Required, StringLength(1500)]
    public string ExpectedResult { get; set; } = string.Empty;

    [Required, StringLength(1500)]
    public string ActualResult { get; set; } = string.Empty;

    public string? EpicId { get; set; }
    public string? StoryId { get; set; }
    public string? MilestoneId { get; set; }
}

public class TriageBugRequest
{
    public BugStatus TargetStatus { get; set; }
    public WorkItemPriority Priority { get; set; } = WorkItemPriority.MEDIUM;
    public string? FixerId { get; set; }
    public string? VerifierId { get; set; }
    public string? Reason { get; set; }
}

public class VerifyBugRequest
{
    public VerificationOutcome Outcome { get; set; }

    [StringLength(1000)]
    public string? Notes { get; set; }
}