using System.ComponentModel.DataAnnotations;

namespace Worksync.Frontend.Models.Projects;

public class CreateProjectRequest
{
    [Required(ErrorMessage = "Project name is required.")]
    [StringLength(100, MinimumLength = 1)]
    public string Name { get; set; } = string.Empty;

    [Required(ErrorMessage = "Project description is required.")]
    [StringLength(2000, MinimumLength = 1)]
    public string Description { get; set; } = string.Empty;

    public DateOnly? StartDate { get; set; }
    public DateOnly? DueDate { get; set; }
    public WorkApproach Approach { get; set; } = WorkApproach.Simple;
    public bool EpicsEnabled { get; set; }
    public bool StoriesEnabled { get; set; }
    public bool MilestonesEnabled { get; set; }
    public bool ListViewEnabled { get; set; }
    public bool BoardViewEnabled { get; set; }
    public bool ReportsEnabled { get; set; } = true;
    public bool ActivityEnabled { get; set; }
    public string DefaultView { get; set; } = "overview";
    public List<AddProjectMemberRequest> InitialMembers { get; set; } = new();
}

public class UpdateProjectRequest
{
    [Required, StringLength(100, MinimumLength = 1)]
    public string Name { get; set; } = string.Empty;

    [Required, StringLength(2000, MinimumLength = 1)]
    public string Description { get; set; } = string.Empty;

    public DateOnly? StartDate { get; set; }
    public DateOnly? DueDate { get; set; }
    public string? Requirements { get; set; }
    public string? Deliverables { get; set; }
    public string? Expectations { get; set; }
}

public class AddProjectMemberRequest
{
    [Required]
    public string UserId { get; set; } = string.Empty;

    public ProjectRole Role { get; set; } = ProjectRole.MEMBER;
    public ProjectResponsibility? Responsibility { get; set; }
}

public class UpdateProjectMemberRequest
{
    public ProjectRole Role { get; set; }
    public ProjectResponsibility? Responsibility { get; set; }
}

public class TransferOwnershipRequest
{
    [Required]
    public string NewOwnerUserId { get; set; } = string.Empty;
}

public class CreateMilestoneRequest
{
    [Required, StringLength(150, MinimumLength = 1)]
    public string Title { get; set; } = string.Empty;

    [StringLength(1000)]
    public string? Description { get; set; }

    public DateOnly? DueDate { get; set; }
}

public class UpdateMilestoneRequest
{
    [Required, StringLength(150, MinimumLength = 1)]
    public string Title { get; set; } = string.Empty;

    [StringLength(1000)]
    public string? Description { get; set; }

    public DateOnly? DueDate { get; set; }
    public bool IsCompleted { get; set; }
}