
using Microsoft.AspNetCore.Components;
using Worksync.Frontend.Models.Projects;
using Worksync.Frontend.Models.WorkItems;
using Worksync.Frontend.Services.Projects;

namespace Worksync.Frontend.Pages;

public partial class Home : IDisposable
{
    [Inject]
    private ProjectContextState ProjectContext { get; set; } = default!;

    [Inject]
    private IProjectService ProjectService { get; set; } = default!;

    [Inject]
    private IWorkItemService WorkItemService { get; set; } = default!;

    [Inject]
    private NavigationManager Navigation { get; set; } = default!;

    private bool _isLoading = true;
    private string? _errorMessage;

    private List<WorkItemRecord> _assignedWork = new();

    // Projects accessible to the signed-in user.
   private List<ProjectSummary> Projects =>
    ProjectContext.AccessibleProjects
        .OrderByDescending(p => p.Status == ProjectStatus.ACTIVE)
        .ThenBy(p => p.Name)
        .ToList();

    // Work assigned to the current user, excluding completed items.
    private List<WorkItemRecord> OpenAssignments =>
        _assignedWork
            .Where(item => item.Status != WorkItemStatus.DONE)
            .OrderBy(item => item.DueDate ?? DateOnly.MaxValue)
            .ToList();

    // Number of projects managed or owned by the current user.
    private int ManagedProjectsCount =>
        ProjectContext.AccessibleProjects.Count(project =>
            project.IsCurrentUserOwner ||
            project.CurrentUserRole == ProjectRole.MANAGER);

    protected override async Task OnInitializedAsync()
    {
        ProjectContext.OnChange += HandleProjectContextChanged;

        await LoadHomeDataAsync();
    }

    private async Task LoadHomeDataAsync()
    {
        _isLoading = true;
        _errorMessage = null;

        try
        {
            var result = await WorkItemService.GetMyAssignedWorkAsync();

            if (result.IsSuccess && result.Data is not null)
            {
                _assignedWork = result.Data;
            }
            else
            {
                _assignedWork = new List<WorkItemRecord>();
                _errorMessage = result.ErrorMessage
                    ?? "Unable to load your assigned work.";
            }
        }
        catch (Exception)
        {
            _errorMessage = "Something went wrong while loading your home page.";
        }
        finally
        {
            _isLoading = false;
        }
    }

    // Opens a project and loads its workspace context.
    private async Task OpenProjectAsync(string projectId)
    {
        if (string.IsNullOrWhiteSpace(projectId))
            return;

        _errorMessage = null;

        try
        {
            var detailTask = ProjectService.GetProjectAsync(projectId);
            var permissionsTask = ProjectService.GetMyPermissionsAsync(projectId);
            var configurationTask = ProjectService.GetConfigurationAsync(projectId);
            var preferenceTask = ProjectService.GetMyPreferencesAsync(projectId);

            await Task.WhenAll(
                detailTask,
                permissionsTask,
                configurationTask,
                preferenceTask);

            var detail = detailTask.Result;
            var permissions = permissionsTask.Result;
            var configuration = configurationTask.Result;
            var preference = preferenceTask.Result;

            if (detail.IsSuccess && detail.Data is not null &&
                permissions.IsSuccess && permissions.Data is not null &&
                configuration.IsSuccess && configuration.Data is not null)
            {
                ProjectContext.SetActiveProject(
                    detail.Data,
                    permissions.Data,
                    configuration.Data,
                    preference.Data);

                Navigation.NavigateTo($"/projects/{projectId}/overview");
            }
            else
            {
                _errorMessage =
                    detail.ErrorMessage ??
                    permissions.ErrorMessage ??
                    configuration.ErrorMessage ??
                    "Unable to open this project.";
            }
        }
        catch (Exception)
        {
            _errorMessage = "Something went wrong while opening this project.";
        }
    }

    // Returns the first letter of a project name.
    private static string ProjectMonogram(string name)
    {
        return string.IsNullOrWhiteSpace(name)
            ? "P"
            : char.ToUpperInvariant(name.Trim()[0]).ToString();
    }

    // Project status CSS class.
    private static string ProjectStatusClass(ProjectStatus status)
    {
        return status switch
        {
            ProjectStatus.ACTIVE => "ws-status-active",
            ProjectStatus.ARCHIVED => "ws-status-archived",
            _ => string.Empty
        };
    }

    // Converts project roles into readable labels.
    private static string RoleLabel(ProjectRole role)
    {
        return role switch
        {
            ProjectRole.MANAGER => "Manager",
            ProjectRole.MEMBER => "Member",
            ProjectRole.VIEWER => "Viewer",
            _ => "Member"
        };
    }

    // Finds the project name associated with an assigned work item.
    private string ProjectNameFor(string projectId)
    {
        return ProjectContext.AccessibleProjects
            .FirstOrDefault(p => p.Id == projectId)?.Name
            ?? "Unknown project";
    }

    // Work item priority CSS class.
    private static string PriorityClass(WorkItemPriority priority)
    {
        return priority switch
        {
            WorkItemPriority.LOW => "ws-priority-low",
            WorkItemPriority.MEDIUM => "ws-priority-medium",
            WorkItemPriority.HIGH => "ws-priority-high",
            WorkItemPriority.URGENT => "ws-priority-urgent",
            _ => string.Empty
        };
    }

    private void HandleProjectContextChanged()
    {
        _ = InvokeAsync(StateHasChanged);
    }

    public void Dispose()
    {
        ProjectContext.OnChange -= HandleProjectContextChanged;
    }
}
