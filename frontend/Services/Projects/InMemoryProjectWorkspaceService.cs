using Worksync.Frontend.Models.Projects;
using Worksync.Frontend.Models.WorkItems;

namespace Worksync.Frontend.Services.Projects;

/// <summary>
/// Local stateful implementation of M2 (Projects) and M3 (Work Items) contracts.
/// Replace with HTTP-backed services once Kelvin & Phoebe merge M2/M3 endpoints into openapi.yaml.
/// </summary>
public class InMemoryProjectWorkspaceService : IProjectService, IWorkItemService
{
    private readonly AuthState _authState;
    private readonly List<ProjectEntry> _store = new();

    public InMemoryProjectWorkspaceService(AuthState authState)
    {
        _authState = authState;
    }

    private string ActorId => _authState.User?.Id ?? "current-user";

    private string ActorName
    {
        get
        {
            var full = $"{_authState.User?.FirstName} {_authState.User?.LastName}".Trim();
            return string.IsNullOrWhiteSpace(full) ? (_authState.User?.Email ?? "Project Owner") : full;
        }
    }

    public Task<ApiResult<List<ProjectSummary>>> GetMyProjectsAsync()
    {
        var list = _store
            .Where(e => e.Members.Any(m => m.UserId == ActorId && m.IsAccountActive))
            .Select(e => ToSummary(e, e.Members.First(m => m.UserId == ActorId)))
            .ToList();

        return Task.FromResult(ApiResult<List<ProjectSummary>>.Ok(list));
    }

    public Task<ApiResult<ProjectDetail>> GetProjectAsync(string projectId)
    {
        var entry = FindAccessible(projectId, out _);
        return Task.FromResult(entry is null
            ? ApiResult<ProjectDetail>.Fail("Project not found or access denied.", 404)
            : ApiResult<ProjectDetail>.Ok(entry.Detail));
    }

    public Task<ApiResult<ProjectDetail>> CreateProjectAsync(CreateProjectRequest request)
    {
        var id = Guid.NewGuid().ToString();
        var now = DateTimeOffset.UtcNow;

        var detail = new ProjectDetail
        {
            Id = id,
            Name = request.Name.Trim(),
            Description = request.Description.Trim(),
            OwnerUserId = ActorId,
            CreatorUserId = ActorId,
            Status = ProjectStatus.ACTIVE,
            Approach = request.Approach,
            StartDate = request.StartDate,
            DueDate = request.DueDate,
            CreatedAt = now,
            UpdatedAt = now
        };

        var config = new ProjectConfigurationModel
        {
            ProjectId = id,
            EpicsEnabled = request.EpicsEnabled,
            StoriesEnabled = request.StoriesEnabled,
            MilestonesEnabled = request.MilestonesEnabled,
            ListViewEnabled = request.ListViewEnabled,
            BoardViewEnabled = request.BoardViewEnabled,
            ReportsEnabled = request.ReportsEnabled,
            ActivityEnabled = request.ActivityEnabled,
            DefaultView = request.DefaultView
        };

        var members = new List<ProjectMemberRecord>
        {
            new()
            {
                UserId = ActorId,
                FirstName = _authState.User?.FirstName ?? "Project",
                LastName = _authState.User?.LastName ?? "Owner",
                Email = _authState.User?.Email ?? "owner@worksync.local",
                Role = ProjectRole.MANAGER,
                Responsibility = null,
                IsOwner = true
            }
        };

        foreach (var initial in request.InitialMembers)
        {
            if (initial.UserId == ActorId || members.Any(m => m.UserId == initial.UserId))
                continue;

            var sample = KnownDemoPeople.FirstOrDefault(p => p.UserId == initial.UserId);
            members.Add(new ProjectMemberRecord
            {
                UserId = initial.UserId,
                FirstName = sample?.FirstName ?? "Team",
                LastName = sample?.LastName ?? "Member",
                Email = sample?.Email ?? $"{initial.UserId}@worksync.local",
                Role = initial.Role,
                Responsibility = initial.Role == ProjectRole.MEMBER
                    ? (initial.Responsibility ?? ProjectResponsibility.DEVELOPER)
                    : null,
                IsOwner = false
            });
        }

        _store.Add(new ProjectEntry(detail, config, members));
        return Task.FromResult(ApiResult<ProjectDetail>.Ok(detail, 201));
    }

    public Task<ApiResult<ProjectDetail>> UpdateProjectAsync(string projectId, UpdateProjectRequest request)
    {
        var entry = FindAccessible(projectId, out var member);
        if (entry is null || member is null)
            return Task.FromResult(ApiResult<ProjectDetail>.Fail("Project not found.", 404));
        if (member.Role != ProjectRole.MANAGER)
            return Task.FromResult(ApiResult<ProjectDetail>.Fail("Manager access is required.", 403));
        if (entry.Detail.Status == ProjectStatus.ARCHIVED)
            return Task.FromResult(ApiResult<ProjectDetail>.Fail("Archived projects cannot be edited.", 409));

        entry.Detail.Name = request.Name.Trim();
        entry.Detail.Description = request.Description.Trim();
        entry.Detail.StartDate = request.StartDate;
        entry.Detail.DueDate = request.DueDate;
        entry.Detail.Requirements = request.Requirements;
        entry.Detail.Deliverables = request.Deliverables;
        entry.Detail.Expectations = request.Expectations;
        entry.Detail.UpdatedAt = DateTimeOffset.UtcNow;

        return Task.FromResult(ApiResult<ProjectDetail>.Ok(entry.Detail));
    }

    public Task<ApiResult<ProjectDetail>> ToggleArchiveAsync(string projectId)
    {
        var entry = FindAccessible(projectId, out var member);
        if (entry is null || member is null)
            return Task.FromResult(ApiResult<ProjectDetail>.Fail("Project not found.", 404));
        if (!member.IsOwner)
            return Task.FromResult(ApiResult<ProjectDetail>.Fail("Only the Project Owner can archive or restore a project.", 403));

        entry.Detail.Status = entry.Detail.Status == ProjectStatus.ACTIVE
            ? ProjectStatus.ARCHIVED
            : ProjectStatus.ACTIVE;
        entry.Detail.UpdatedAt = DateTimeOffset.UtcNow;

        return Task.FromResult(ApiResult<ProjectDetail>.Ok(entry.Detail));
    }

    public Task<ApiResult<EffectiveProjectPermissions>> GetMyPermissionsAsync(string projectId)
    {
        var entry = FindAccessible(projectId, out var member);
        if (entry is null || member is null)
            return Task.FromResult(ApiResult<EffectiveProjectPermissions>.Fail("Project not found.", 404));

        return Task.FromResult(ApiResult<EffectiveProjectPermissions>.Ok(BuildPermissions(projectId, member)));
    }

    public Task<ApiResult<ProjectConfigurationModel>> GetConfigurationAsync(string projectId)
    {
        var entry = FindAccessible(projectId, out _);
        return Task.FromResult(entry is null
            ? ApiResult<ProjectConfigurationModel>.Fail("Project not found.", 404)
            : ApiResult<ProjectConfigurationModel>.Ok(entry.Config));
    }

    public Task<ApiResult<ProjectConfigurationModel>> UpdateConfigurationAsync(string projectId, ProjectConfigurationModel config)
    {
        var entry = FindAccessible(projectId, out var member);
        if (entry is null || member is null)
            return Task.FromResult(ApiResult<ProjectConfigurationModel>.Fail("Project not found.", 404));
        if (member.Role != ProjectRole.MANAGER)
            return Task.FromResult(ApiResult<ProjectConfigurationModel>.Fail("Only Managers can update project configuration.", 403));
        if (entry.Detail.Status == ProjectStatus.ARCHIVED)
            return Task.FromResult(ApiResult<ProjectConfigurationModel>.Fail("Project is archived.", 409));

        entry.Config.EpicsEnabled = config.EpicsEnabled;
        entry.Config.StoriesEnabled = config.StoriesEnabled;
        entry.Config.MilestonesEnabled = config.MilestonesEnabled;
        entry.Config.ListViewEnabled = config.ListViewEnabled;
        entry.Config.BoardViewEnabled = config.BoardViewEnabled;
        entry.Config.ReportsEnabled = config.ReportsEnabled;
        entry.Config.ActivityEnabled = config.ActivityEnabled;
        entry.Config.DefaultView = config.DefaultView;

        return Task.FromResult(ApiResult<ProjectConfigurationModel>.Ok(entry.Config));
    }
    //This section handles:- Project preferences,- Viewing team members,- Adding and updating members,- Removing members,- Transferring project ownership,
        public Task<ApiResult<ProjectPreferenceModel>> GetMyPreferencesAsync(string projectId)
    {
        var entry = FindAccessible(projectId, out _);
        if (entry is null)
            return Task.FromResult(ApiResult<ProjectPreferenceModel>.Fail("Project not found.", 404));

        if (!entry.Preferences.TryGetValue(ActorId, out var pref))
        {
            pref = new ProjectPreferenceModel { ProjectId = projectId };
            entry.Preferences[ActorId] = pref;
        }

        return Task.FromResult(ApiResult<ProjectPreferenceModel>.Ok(pref));
    }

    public Task<ApiResult<ProjectPreferenceModel>> UpdateMyPreferencesAsync(string projectId, ProjectPreferenceModel preference)
    {
        var entry = FindAccessible(projectId, out _);
        if (entry is null)
            return Task.FromResult(ApiResult<ProjectPreferenceModel>.Fail("Project not found.", 404));

        preference.ProjectId = projectId;
        entry.Preferences[ActorId] = preference;
        return Task.FromResult(ApiResult<ProjectPreferenceModel>.Ok(preference));
    }

    public Task<ApiResult<List<ProjectMemberRecord>>> GetMembersAsync(string projectId)
    {
        var entry = FindAccessible(projectId, out var member);
        if (entry is null || member is null)
            return Task.FromResult(ApiResult<List<ProjectMemberRecord>>.Fail("Project not found.", 404));
        if (member.Role == ProjectRole.VIEWER)
            return Task.FromResult(ApiResult<List<ProjectMemberRecord>>.Fail("Viewers cannot access internal team directory.", 403));

        return Task.FromResult(ApiResult<List<ProjectMemberRecord>>.Ok(entry.Members.ToList()));
    }

    public Task<ApiResult<ProjectMemberRecord>> AddMemberAsync(string projectId, AddProjectMemberRequest request)
    {
        var entry = FindAccessible(projectId, out var actor);
        if (entry is null || actor is null)
            return Task.FromResult(ApiResult<ProjectMemberRecord>.Fail("Project not found.", 404));
        if (actor.Role != ProjectRole.MANAGER)
            return Task.FromResult(ApiResult<ProjectMemberRecord>.Fail("Manager access required.", 403));
        if (request.Role == ProjectRole.MANAGER && !actor.IsOwner)
            return Task.FromResult(ApiResult<ProjectMemberRecord>.Fail("Only the Project Owner can appoint Managers.", 403));
        if (entry.Members.Any(m => m.UserId == request.UserId))
            return Task.FromResult(ApiResult<ProjectMemberRecord>.Fail("User is already a project member.", 409));

        var known = KnownDemoPeople.FirstOrDefault(p => p.UserId == request.UserId);
        var added = new ProjectMemberRecord
        {
            UserId = request.UserId,
            FirstName = known?.FirstName ?? "Workspace",
            LastName = known?.LastName ?? "User",
            Email = known?.Email ?? $"{request.UserId}@worksync.local",
            Role = request.Role,
            Responsibility = request.Role == ProjectRole.MEMBER
                ? (request.Responsibility ?? ProjectResponsibility.DEVELOPER)
                : null
        };
        entry.Members.Add(added);
        return Task.FromResult(ApiResult<ProjectMemberRecord>.Ok(added, 201));
    }

    public Task<ApiResult<ProjectMemberRecord>> UpdateMemberAsync(string projectId, string userId, UpdateProjectMemberRequest request)
    {
        var entry = FindAccessible(projectId, out var actor);
        var target = entry?.Members.FirstOrDefault(m => m.UserId == userId);
        if (entry is null || actor is null || target is null)
            return Task.FromResult(ApiResult<ProjectMemberRecord>.Fail("Member not found.", 404));
        if (actor.Role != ProjectRole.MANAGER)
            return Task.FromResult(ApiResult<ProjectMemberRecord>.Fail("Manager access required.", 403));
        if (target.IsOwner)
            return Task.FromResult(ApiResult<ProjectMemberRecord>.Fail("Transfer ownership before changing the Project Owner.", 409));
        if ((target.Role == ProjectRole.MANAGER || request.Role == ProjectRole.MANAGER) && !actor.IsOwner)
            return Task.FromResult(ApiResult<ProjectMemberRecord>.Fail("Only the Project Owner can manage Managers.", 403));

        target.Role = request.Role;
        target.Responsibility = request.Role == ProjectRole.MEMBER
            ? (request.Responsibility ?? ProjectResponsibility.DEVELOPER)
            : null;
        return Task.FromResult(ApiResult<ProjectMemberRecord>.Ok(target));
    }

    public Task<ApiResult> RemoveMemberAsync(string projectId, string userId)
    {
        var entry = FindAccessible(projectId, out var actor);
        var target = entry?.Members.FirstOrDefault(m => m.UserId == userId);
        if (entry is null || actor is null || target is null)
            return Task.FromResult(ApiResult.Fail("Member not found.", 404));
        if (actor.Role != ProjectRole.MANAGER)
            return Task.FromResult(ApiResult.Fail("Manager access required.", 403));
        if (target.IsOwner)
            return Task.FromResult(ApiResult.Fail("Cannot remove the Project Owner.", 409));
        if (target.Role == ProjectRole.MANAGER && !actor.IsOwner)
            return Task.FromResult(ApiResult.Fail("Only the Project Owner can remove Managers.", 403));

        entry.Members.Remove(target);
        return Task.FromResult(ApiResult.Ok(204));
    }

    public Task<ApiResult> TransferOwnershipAsync(string projectId, TransferOwnershipRequest request)
    {
        var entry = FindAccessible(projectId, out var actor);
        var target = entry?.Members.FirstOrDefault(m => m.UserId == request.NewOwnerUserId);
        if (entry is null || actor is null || target is null)
            return Task.FromResult(ApiResult.Fail("Target member not found.", 404));
        if (!actor.IsOwner)
            return Task.FromResult(ApiResult.Fail("Only the current Owner can transfer ownership.", 403));
        if (target.Role != ProjectRole.MANAGER || !target.IsAccountActive)
            return Task.FromResult(ApiResult.Fail("Ownership can only be transferred to an active existing Manager.", 409));

        actor.IsOwner = false;
        target.IsOwner = true;
        entry.Detail.OwnerUserId = target.UserId;
        return Task.FromResult(ApiResult.Ok());
    }
    //this part adds the milestone operations and the client delivery
        public Task<ApiResult<List<ProjectMilestoneModel>>> GetMilestonesAsync(string projectId)
    {
        var entry = FindAccessible(projectId, out _);
        return Task.FromResult(entry is null
            ? ApiResult<List<ProjectMilestoneModel>>.Fail("Project not found.", 404)
            : ApiResult<List<ProjectMilestoneModel>>.Ok(entry.Milestones.ToList()));
    }

    public Task<ApiResult<ProjectMilestoneModel>> CreateMilestoneAsync(string projectId, CreateMilestoneRequest request)
    {
        var entry = FindAccessible(projectId, out var actor);
        if (entry is null || actor is null)
            return Task.FromResult(ApiResult<ProjectMilestoneModel>.Fail("Project not found.", 404));
        if (actor.Role != ProjectRole.MANAGER)
            return Task.FromResult(ApiResult<ProjectMilestoneModel>.Fail("Manager access required.", 403));

        var milestone = new ProjectMilestoneModel
        {
            Id = Guid.NewGuid().ToString(),
            ProjectId = projectId,
            Title = request.Title.Trim(),
            Description = request.Description?.Trim(),
            DueDate = request.DueDate
        };
        entry.Milestones.Add(milestone);
        return Task.FromResult(ApiResult<ProjectMilestoneModel>.Ok(milestone, 201));
    }

    public Task<ApiResult<ProjectMilestoneModel>> UpdateMilestoneAsync(string projectId, string milestoneId, UpdateMilestoneRequest request)
    {
        var entry = FindAccessible(projectId, out var actor);
        var milestone = entry?.Milestones.FirstOrDefault(m => m.Id == milestoneId);
        if (entry is null || actor is null || milestone is null)
            return Task.FromResult(ApiResult<ProjectMilestoneModel>.Fail("Milestone not found.", 404));
        if (actor.Role != ProjectRole.MANAGER)
            return Task.FromResult(ApiResult<ProjectMilestoneModel>.Fail("Manager access required.", 403));

        milestone.Title = request.Title.Trim();
        milestone.Description = request.Description?.Trim();
        milestone.DueDate = request.DueDate;
        milestone.IsCompleted = request.IsCompleted;
        return Task.FromResult(ApiResult<ProjectMilestoneModel>.Ok(milestone));
    }

    public Task<ApiResult<ClientDeliverySummary>> GetDeliverySummaryAsync(string projectId)
    {
        var entry = FindAccessible(projectId, out _);
        if (entry is null)
            return Task.FromResult(ApiResult<ClientDeliverySummary>.Fail("Project not found.", 404));

        var summary = new ClientDeliverySummary
        {
            ProjectId = entry.Detail.Id,
            ProjectName = entry.Detail.Name,
            Description = entry.Detail.Description,
            Status = entry.Detail.Status.ToString(),
            DueDate = entry.Detail.DueDate,
            TotalItems = entry.WorkItems.Count,
            CompletedItems = entry.WorkItems.Count(w => w.Status == WorkItemStatus.DONE),
            MilestoneCount = entry.Milestones.Count
        };
        return Task.FromResult(ApiResult<ClientDeliverySummary>.Ok(summary));
    }
    //This part covers retrieving work items, summaries, creating work items, updating their status, assigning them, and toggling subtasks.
        public Task<ApiResult<List<WorkItemRecord>>> GetProjectWorkItemsAsync(string projectId)
    {
        var entry = FindAccessible(projectId, out var actor);
        if (entry is null || actor is null)
            return Task.FromResult(ApiResult<List<WorkItemRecord>>.Fail("Project not found.", 404));
        if (actor.Role == ProjectRole.VIEWER)
            return Task.FromResult(ApiResult<List<WorkItemRecord>>.Fail("Viewers cannot access internal work items.", 403));

        return Task.FromResult(ApiResult<List<WorkItemRecord>>.Ok(entry.WorkItems.ToList()));
    }

    public Task<ApiResult<List<WorkItemRecord>>> GetMyAssignedWorkAsync()
    {
        var items = _store
            .Where(e => e.Members.Any(m => m.UserId == ActorId && m.Role != ProjectRole.VIEWER))
            .SelectMany(e => e.WorkItems)
            .Where(w => w.AssigneeId == ActorId)
            .ToList();

        return Task.FromResult(ApiResult<List<WorkItemRecord>>.Ok(items));
    }

    public Task<ApiResult<WorkItemSummary>> GetProjectSummaryAsync(string projectId)
    {
        var entry = FindAccessible(projectId, out var actor);
        if (entry is null || actor is null)
            return Task.FromResult(ApiResult<WorkItemSummary>.Fail("Project not found.", 404));
        if (actor.Role == ProjectRole.VIEWER)
            return Task.FromResult(ApiResult<WorkItemSummary>.Fail("Viewers cannot access internal work summary.", 403));

        var bugs = entry.WorkItems.Where(w => w.Type == WorkItemType.BUG).ToList();
        var summary = new WorkItemSummary
        {
            TotalWork = entry.WorkItems.Count,
            TodoCount = entry.WorkItems.Count(w => w.Status == WorkItemStatus.TODO),
            InProgressCount = entry.WorkItems.Count(w => w.Status == WorkItemStatus.IN_PROGRESS),
            InReviewCount = entry.WorkItems.Count(w => w.Status == WorkItemStatus.IN_REVIEW),
            DoneCount = entry.WorkItems.Count(w => w.Status == WorkItemStatus.DONE),
            OpenBugCount = bugs.Count(b => b.BugDetails?.BugStatus is not (BugStatus.CLOSED or BugStatus.INVALID or BugStatus.DUPLICATE)),
            TriageQueueCount = bugs.Count(b => b.BugDetails?.BugStatus is BugStatus.REPORTED or BugStatus.TRIAGE or BugStatus.REOPENED)
        };
        return Task.FromResult(ApiResult<WorkItemSummary>.Ok(summary));
    }

    public Task<ApiResult<WorkItemRecord>> CreateWorkItemAsync(string projectId, CreateWorkItemRequest request)
    {
        var entry = FindAccessible(projectId, out var actor);
        if (entry is null || actor is null)
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("Project not found.", 404));
        if (entry.Detail.Status == ProjectStatus.ARCHIVED)
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("Project is archived.", 409));

        var perms = BuildPermissions(projectId, actor);
        var required = request.Type switch
        {
            WorkItemType.EPIC => ProjectAction.CREATE_EPIC,
            WorkItemType.STORY => ProjectAction.CREATE_STORY,
            _ => ProjectAction.CREATE_TASK
        };

        if (!perms.AllowedActions.Contains(required))
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("You do not have permission to create this work item type.", 403));

        var assignee = actor.Role == ProjectRole.MEMBER && actor.Responsibility == ProjectResponsibility.DEVELOPER
            ? ActorId
            : request.AssigneeId;

        var now = DateTimeOffset.UtcNow;
        var item = new WorkItemRecord
        {
            Id = Guid.NewGuid().ToString(),
            ProjectId = projectId,
            Type = request.Type,
            Title = request.Title.Trim(),
            Description = request.Description?.Trim(),
            Priority = request.Priority,
            Status = WorkItemStatus.TODO,
            CreatorId = ActorId,
            AssigneeId = assignee,
            EpicId = request.EpicId,
            StoryId = request.StoryId,
            MilestoneId = request.MilestoneId,
            DueDate = request.DueDate,
            Subtasks = request.SubtaskTitles
                .Where(t => !string.IsNullOrWhiteSpace(t))
                .Select(t => new SubtaskItem { Id = Guid.NewGuid().ToString(), Title = t.Trim() })
                .ToList(),
            CreatedAt = now,
            UpdatedAt = now
        };

        entry.WorkItems.Add(item);
        return Task.FromResult(ApiResult<WorkItemRecord>.Ok(item, 201));
    }

    public Task<ApiResult<WorkItemRecord>> UpdateWorkItemStatusAsync(string projectId, string workItemId, WorkItemStatus status)
    {
        var entry = FindAccessible(projectId, out var actor);
        var item = entry?.WorkItems.FirstOrDefault(w => w.Id == workItemId);
        if (entry is null || actor is null || item is null)
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("Work item not found.", 404));
        if (entry.Detail.Status == ProjectStatus.ARCHIVED)
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("Project is archived.", 409));
        if (actor.Role == ProjectRole.VIEWER || (actor.Role == ProjectRole.MEMBER && actor.Responsibility == ProjectResponsibility.QA_TESTER))
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("Not permitted to move work item status.", 403));
        if (actor.Role == ProjectRole.MEMBER && actor.Responsibility == ProjectResponsibility.DEVELOPER && item.AssigneeId != ActorId)
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("Developers can only update work assigned to themselves.", 403));

        item.Status = status;
        item.UpdatedAt = DateTimeOffset.UtcNow;
        return Task.FromResult(ApiResult<WorkItemRecord>.Ok(item));
    }

    public Task<ApiResult<WorkItemRecord>> AssignWorkItemAsync(string projectId, string workItemId, string? assigneeId)
    {
        var entry = FindAccessible(projectId, out var actor);
        var item = entry?.WorkItems.FirstOrDefault(w => w.Id == workItemId);
        if (entry is null || actor is null || item is null)
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("Work item not found.", 404));
        if (!BuildPermissions(projectId, actor).AllowedActions.Contains(ProjectAction.ASSIGN_WORK))
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("Assignment permission required.", 403));

        item.AssigneeId = assigneeId;
        item.UpdatedAt = DateTimeOffset.UtcNow;
        return Task.FromResult(ApiResult<WorkItemRecord>.Ok(item));
    }

    public Task<ApiResult<WorkItemRecord>> ToggleSubtaskAsync(string projectId, string workItemId, string subtaskId, bool isDone)
    {
        var entry = FindAccessible(projectId, out var actor);
        var item = entry?.WorkItems.FirstOrDefault(w => w.Id == workItemId);
        var subtask = item?.Subtasks.FirstOrDefault(s => s.Id == subtaskId);
        if (entry is null || actor is null || item is null || subtask is null)
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("Subtask not found.", 404));
        if (actor.Role == ProjectRole.VIEWER)
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("Read-only access.", 403));

        subtask.IsDone = isDone;
        item.UpdatedAt = DateTimeOffset.UtcNow;
        return Task.FromResult(ApiResult<WorkItemRecord>.Ok(item));
    }
    //This part adds bug reporting, triaging, and submitting a bug fix for verification.
        public Task<ApiResult<WorkItemRecord>> ReportBugAsync(string projectId, ReportBugRequest request)
    {
        var entry = FindAccessible(projectId, out var actor);
        if (entry is null || actor is null)
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("Project not found.", 404));
        if (!BuildPermissions(projectId, actor).AllowedActions.Contains(ProjectAction.REPORT_BUG))
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("Not permitted to report bugs.", 403));

        var bugId = Guid.NewGuid().ToString();
        var now = DateTimeOffset.UtcNow;

        var bug = new WorkItemRecord
        {
            Id = bugId,
            ProjectId = projectId,
            Type = WorkItemType.BUG,
            Title = request.Title.Trim(),
            Description = request.ActualResult.Trim(),
            Priority = request.Priority,
            Status = WorkItemStatus.TODO,
            CreatorId = ActorId,
            EpicId = request.EpicId,
            StoryId = request.StoryId,
            MilestoneId = request.MilestoneId,
            CreatedAt = now,
            UpdatedAt = now,
            BugDetails = new BugDetailsRecord
            {
                WorkItemId = bugId,
                Severity = request.Severity,
                AffectedArea = request.AffectedArea.Trim(),
                StepsToReproduce = request.StepsToReproduce.Trim(),
                ExpectedResult = request.ExpectedResult.Trim(),
                ActualResult = request.ActualResult.Trim(),
                ReporterId = ActorId,
                BugStatus = BugStatus.REPORTED,
                History =
                {
                    new WorkItemHistoryRecord
                    {
                        Id = Guid.NewGuid().ToString(),
                        ActorId = ActorId,
                        ActorName = ActorName,
                        Action = "REPORTED",
                        Note = "Bug reported",
                        OccurredAt = now
                    }
                }
            }
        };

        entry.WorkItems.Add(bug);
        return Task.FromResult(ApiResult<WorkItemRecord>.Ok(bug, 201));
    }

    public Task<ApiResult<WorkItemRecord>> TriageBugAsync(string projectId, string bugId, TriageBugRequest request)
    {
        var entry = FindAccessible(projectId, out var actor);
        var item = entry?.WorkItems.FirstOrDefault(w => w.Id == bugId && w.BugDetails is not null);
        if (entry is null || actor is null || item?.BugDetails is null)
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("Bug not found.", 404));
        if (!BuildPermissions(projectId, actor).AllowedActions.Contains(ProjectAction.TRIAGE_BUG))
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("Triage permission required.", 403));

        if (!string.IsNullOrWhiteSpace(request.FixerId)
            && !string.IsNullOrWhiteSpace(request.VerifierId)
            && request.FixerId == request.VerifierId)
        {
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("The fixer cannot verify their own bug fix.", 409));
        }

        var details = item.BugDetails;
        item.Priority = request.Priority;
        item.AssigneeId = request.FixerId;
        details.FixerId = request.FixerId;
        details.VerifierId = request.VerifierId;
        details.BugStatus = request.TargetStatus;
        details.TriageReason = request.Reason?.Trim();
        item.Status = request.TargetStatus switch
        {
            BugStatus.CLOSED or BugStatus.INVALID or BugStatus.DUPLICATE => WorkItemStatus.DONE,
            BugStatus.READY_FOR_VERIFICATION or BugStatus.VERIFIED => WorkItemStatus.IN_REVIEW,
            BugStatus.IN_PROGRESS or BugStatus.ASSIGNED or BugStatus.TRIAGE or BugStatus.REOPENED => WorkItemStatus.IN_PROGRESS,
            _ => WorkItemStatus.TODO
        };
        details.History.Add(new WorkItemHistoryRecord
        {
            Id = Guid.NewGuid().ToString(),
            ActorId = ActorId,
            ActorName = ActorName,
            Action = request.TargetStatus.ToString(),
            Note = string.IsNullOrWhiteSpace(request.Reason) ? "Triaged" : request.Reason.Trim(),
            OccurredAt = DateTimeOffset.UtcNow
        });

        return Task.FromResult(ApiResult<WorkItemRecord>.Ok(item));
    }

    public Task<ApiResult<WorkItemRecord>> SubmitBugFixAsync(string projectId, string bugId)
    {
        var entry = FindAccessible(projectId, out var actor);
        var item = entry?.WorkItems.FirstOrDefault(w => w.Id == bugId && w.BugDetails is not null);
        if (entry is null || actor is null || item?.BugDetails is null)
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("Bug not found.", 404));

        var details = item.BugDetails;
        if (details.FixerId != ActorId)
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("Only the assigned fixer can submit a fix.", 403));
        if (string.IsNullOrWhiteSpace(details.VerifierId))
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("A verifier must be designated before submitting for verification.", 409));
        if (details.VerifierId == ActorId)
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("You cannot verify your own bug fix.", 409));

        details.BugStatus = BugStatus.READY_FOR_VERIFICATION;
        item.Status = WorkItemStatus.IN_REVIEW;
        details.History.Add(new WorkItemHistoryRecord
        {
            Id = Guid.NewGuid().ToString(),
            ActorId = ActorId,
            ActorName = ActorName,
            Action = "SUBMITTED_FOR_VERIFICATION",
            Note = "Fix submitted",
            OccurredAt = DateTimeOffset.UtcNow
        });

        return Task.FromResult(ApiResult<WorkItemRecord>.Ok(item));
    }
        public Task<ApiResult<WorkItemRecord>> VerifyBugAsync(string projectId, string bugId, VerifyBugRequest request)
    {
        var entry = FindAccessible(projectId, out var actor);
        var item = entry?.WorkItems.FirstOrDefault(w => w.Id == bugId && w.BugDetails is not null);
        if (entry is null || actor is null || item?.BugDetails is null)
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("Bug not found.", 404));

        var details = item.BugDetails;
        if (details.VerifierId != ActorId)
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("Only the designated verifier can record verification.", 403));
        if (details.FixerId == ActorId)
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("Self-verification is not permitted.", 409));
        if (details.BugStatus != BugStatus.READY_FOR_VERIFICATION)
            return Task.FromResult(ApiResult<WorkItemRecord>.Fail("Bug is not ready for verification.", 409));

        var passed = request.Outcome == VerificationOutcome.PASS;
        details.BugStatus = passed ? BugStatus.CLOSED : BugStatus.REOPENED;
        item.Status = passed ? WorkItemStatus.DONE : WorkItemStatus.IN_PROGRESS;
        details.Verifications.Add(new BugVerificationRecord
        {
            Id = Guid.NewGuid().ToString(),
            VerifierId = ActorId,
            VerifierName = ActorName,
            Outcome = request.Outcome,
            Notes = request.Notes?.Trim(),
            VerifiedAt = DateTimeOffset.UtcNow
        });
        details.History.Add(new WorkItemHistoryRecord
        {
            Id = Guid.NewGuid().ToString(),
            ActorId = ActorId,
            ActorName = ActorName,
            Action = passed ? "VERIFIED_CLOSED" : "REOPENED",
            Note = request.Notes?.Trim(),
            OccurredAt = DateTimeOffset.UtcNow
        });

        return Task.FromResult(ApiResult<WorkItemRecord>.Ok(item));
    }

    public async Task<ProjectSummary> SeedSampleWorkspaceAsync()
    {
        var existing = _store.FirstOrDefault(e => e.Detail.Name == "HR Management System");
        if (existing is not null)
            return ToSummary(existing, existing.Members.First(m => m.UserId == ActorId));

        var createRes = await CreateProjectAsync(new CreateProjectRequest
        {
            Name = "HR Management System",
            Description = "A secure platform for employee records, leave requests and payroll coordination.",
            Approach = WorkApproach.Kanban,
            EpicsEnabled = true,
            StoriesEnabled = true,
            MilestonesEnabled = true,
            ListViewEnabled = true,
            BoardViewEnabled = true,
            ReportsEnabled = true,
            ActivityEnabled = true,
            DefaultView = "overview",
            InitialMembers =
            {
                new AddProjectMemberRequest { UserId = "sarah", Role = ProjectRole.MEMBER, Responsibility = ProjectResponsibility.TECHNICAL_LEAD },
                new AddProjectMemberRequest { UserId = "brian", Role = ProjectRole.MEMBER, Responsibility = ProjectResponsibility.DEVELOPER },
                new AddProjectMemberRequest { UserId = "diana", Role = ProjectRole.MEMBER, Responsibility = ProjectResponsibility.QA_TESTER }
            }
        });

        var entry = _store.First(e => e.Detail.Id == createRes.Data!.Id);
        return ToSummary(entry, entry.Members.First(m => m.UserId == ActorId));
    }

    public static IReadOnlyList<ProjectMemberRecord> KnownDemoPeople { get; } =
    [
        new() { UserId = "sarah", FirstName = "Sarah", LastName = "Wanjiku", Email = "sarah@worksync.example" },
        new() { UserId = "brian", FirstName = "Brian", LastName = "Otieno", Email = "brian@worksync.example" },
        new() { UserId = "diana", FirstName = "Diana", LastName = "Achieng", Email = "diana@worksync.example" },
        new() { UserId = "noah", FirstName = "Noah", LastName = "Mwangi", Email = "noah@worksync.example" }
    ];

    private ProjectEntry? FindAccessible(string projectId, out ProjectMemberRecord? member)
    {
        var entry = _store.FirstOrDefault(e => e.Detail.Id == projectId);
        member = entry?.Members.FirstOrDefault(m => m.UserId == ActorId && m.IsAccountActive);
        return member is null ? null : entry;
    }

    private static ProjectSummary ToSummary(ProjectEntry entry, ProjectMemberRecord member) => new()
    {
        Id = entry.Detail.Id,
        Name = entry.Detail.Name,
        Description = entry.Detail.Description,
        Status = entry.Detail.Status,
        CurrentUserRole = member.Role,
        CurrentUserResponsibility = member.Responsibility,
        IsCurrentUserOwner = member.IsOwner,
        MemberCount = entry.Members.Count,
        OpenWorkCount = entry.WorkItems.Count(w => w.Status != WorkItemStatus.DONE),
        StartDate = entry.Detail.StartDate,
        DueDate = entry.Detail.DueDate
    };

    private static EffectiveProjectPermissions BuildPermissions(string projectId, ProjectMemberRecord member)
    {
        var actions = new HashSet<ProjectAction> { ProjectAction.VIEW_DELIVERY };

        if (member.Role != ProjectRole.VIEWER)
        {
            actions.Add(ProjectAction.VIEW_INTERNAL_WORK);
            actions.Add(ProjectAction.REPORT_BUG);
            actions.Add(ProjectAction.UPDATE_ASSIGNED_WORK);
        }

        if (member.Role == ProjectRole.MANAGER
            || (member.Role == ProjectRole.MEMBER && member.Responsibility == ProjectResponsibility.TECHNICAL_LEAD))
        {
            actions.Add(ProjectAction.CREATE_EPIC);
            actions.Add(ProjectAction.CREATE_STORY);
            actions.Add(ProjectAction.CREATE_TASK);
            actions.Add(ProjectAction.ASSIGN_WORK);
            actions.Add(ProjectAction.TRIAGE_BUG);
            actions.Add(ProjectAction.DESIGNATE_BUG_VERIFIER);
        }
        else if (member.Role == ProjectRole.MEMBER && member.Responsibility == ProjectResponsibility.DEVELOPER)
        {
            actions.Add(ProjectAction.CREATE_TASK);
        }

        return new EffectiveProjectPermissions
        {
            ProjectId = projectId,
            Role = member.Role,
            Responsibility = member.Responsibility,
            IsOwner = member.IsOwner,
            AllowedActions = actions
        };
    }

    private sealed class ProjectEntry(
        ProjectDetail detail,
        ProjectConfigurationModel config,
        List<ProjectMemberRecord> members)
    {
        public ProjectDetail Detail { get; } = detail;
        public ProjectConfigurationModel Config { get; } = config;
        public List<ProjectMemberRecord> Members { get; } = members;
        public List<ProjectMilestoneModel> Milestones { get; } = new();
        public List<WorkItemRecord> WorkItems { get; } = new();
        public Dictionary<string, ProjectPreferenceModel> Preferences { get; } = new();
    }
}