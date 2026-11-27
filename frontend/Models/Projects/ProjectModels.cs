namespace Worksync.Frontend.Models.Projects;

public class ProjectSummary
{
    public string Id { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public ProjectStatus Status { get; set; } = ProjectStatus.ACTIVE;
    public ProjectRole CurrentUserRole { get; set; } = ProjectRole.VIEWER;
    public ProjectResponsibility? CurrentUserResponsibility { get; set; }
    public bool IsCurrentUserOwner { get; set; }
    public int MemberCount { get; set; }
    public int OpenWorkCount { get; set; }
    public DateOnly? StartDate { get; set; }
    public DateOnly? DueDate { get; set; }
}

public class ProjectDetail
{
    public string Id { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public string? Requirements { get; set; }
    public string? Deliverables { get; set; }
    public string? Expectations { get; set; }
    public string OwnerUserId { get; set; } = string.Empty;
    public string CreatorUserId { get; set; } = string.Empty;
    public ProjectStatus Status { get; set; } = ProjectStatus.ACTIVE;
    public WorkApproach Approach { get; set; } = WorkApproach.Simple;
    public DateOnly? StartDate { get; set; }
    public DateOnly? DueDate { get; set; }
    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset UpdatedAt { get; set; }
}

public class ProjectMemberRecord
{
    public string UserId { get; set; } = string.Empty;
    public string FirstName { get; set; } = string.Empty;
    public string LastName { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public bool IsAccountActive { get; set; } = true;
    public ProjectRole Role { get; set; } = ProjectRole.MEMBER;
    public ProjectResponsibility? Responsibility { get; set; }
    public bool IsOwner { get; set; }

    public string FullName => $"{FirstName} {LastName}".Trim();
}

public class ProjectConfigurationModel
{
    public string ProjectId { get; set; } = string.Empty;
    public bool EpicsEnabled { get; set; }
    public bool StoriesEnabled { get; set; }
    public bool MilestonesEnabled { get; set; }
    public bool ListViewEnabled { get; set; }
    public bool BoardViewEnabled { get; set; }
    public bool ReportsEnabled { get; set; } = true;
    public bool ActivityEnabled { get; set; }
    public string DefaultView { get; set; } = "overview";
}

public class ProjectPreferenceModel
{
    public string ProjectId { get; set; } = string.Empty;
    public string LandingPage { get; set; } = "auto";
    public string PreferredView { get; set; } = "auto";
    public string DefaultFilter { get; set; } = "mine";
    public string SortBy { get; set; } = "priority";
    public string GroupBy { get; set; } = "status";
}

public class ProjectMilestoneModel
{
    public string Id { get; set; } = string.Empty;
    public string ProjectId { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public string? Description { get; set; }
    public DateOnly? DueDate { get; set; }
    public bool IsCompleted { get; set; }
}

public class EffectiveProjectPermissions
{
    public string ProjectId { get; set; } = string.Empty;
    public ProjectRole Role { get; set; } = ProjectRole.VIEWER;
    public ProjectResponsibility? Responsibility { get; set; }
    public bool IsOwner { get; set; }
    public HashSet<ProjectAction> AllowedActions { get; set; } = new();
}