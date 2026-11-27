
using Microsoft.AspNetCore.Components;
using Microsoft.AspNetCore.Components.Routing;
using MudBlazor;
using Worksync.Frontend.Extensions;
using Worksync.Frontend.Models.Projects;
using Worksync.Frontend.Services;
using Worksync.Frontend.Services.Projects;

namespace Worksync.Frontend.Components.Layout;

public partial class MainLayout
{
    [Inject] private AuthState Auth { get; set; } = default!;
    [Inject] private ProjectContextState ProjectContext { get; set; } = default!;
    [Inject] private IProjectService ProjectService { get; set; } = default!;
    [Inject] private NavigationManager Navigation { get; set; } = default!;
    [Inject] private IDialogService DialogService { get; set; } = default!;

    private bool _drawerOpen = true;
    private bool _viewsExpanded = true;

    // Retained for your existing topbar.
    private string GreetingName => FirstNameOrDisplay;

    private string FirstNameOrDisplay =>
        !string.IsNullOrWhiteSpace(Auth.User?.FirstName)
            ? Auth.User!.FirstName.Trim()
            : (Auth.User?.Email ?? "Account");

    private string WorkspaceRoleLabel => ProjectContext.WorkspaceRole switch
    {
        EffectiveWorkspaceRole.Owner => "Project Owner",
        EffectiveWorkspaceRole.Manager => "Project Manager",
        EffectiveWorkspaceRole.TechnicalLead => "Technical Lead",
        EffectiveWorkspaceRole.Developer => "Developer",
        EffectiveWorkspaceRole.QaTester => "QA Tester",
        EffectiveWorkspaceRole.Viewer => "Viewer",
        _ => "Member"
    };

    private string CurrentRoute => Navigation.ToBaseRelativePath(Navigation.Uri)
        .Split('?', '#')[0]
        .Trim('/')
        .ToLowerInvariant();

    private string SectionName
    {
        get
        {
            if (CurrentRoute.StartsWith("projects/") &&
                CurrentRoute != "projects/new" &&
                ProjectContext.ActiveProject is not null)
                return ProjectContext.ActiveProject.Name;

            if (CurrentRoute.StartsWith("admin") || CurrentRoute == "dashboard")
                return "Administration";

            if (CurrentRoute == "profile")
                return "My Account";

            return "Workspace";
        }
    }

    private string PageTitleText
    {
        get
        {
            if (CurrentRoute == "home") return "Home";
            if (CurrentRoute == "dashboard") return "Overview";
            if (CurrentRoute == "projects") return "My Projects";
            if (CurrentRoute == "projects/new") return "Create Project";
            if (CurrentRoute == "admin/approvals") return "Approvals";
            if (CurrentRoute == "admin/users") return "User Management";
            if (CurrentRoute == "profile") return "My Profile";
            if (CurrentRoute == "pending-approval") return "Approval Status";
            if (CurrentRoute.EndsWith("/planning")) return "Planning";
            if (CurrentRoute.EndsWith("/list")) return "List view";
            if (CurrentRoute.EndsWith("/board")) return "Board view";
            if (CurrentRoute.EndsWith("/team")) return "Team & Access";
            if (CurrentRoute.EndsWith("/reports")) return "Reports";
            if (CurrentRoute.EndsWith("/configuration")) return "Workflow Configuration";
            if (CurrentRoute.EndsWith("/settings")) return "Project Settings";
            if (CurrentRoute.EndsWith("/preferences")) return "My Preferences";
            if (CurrentRoute.EndsWith("/workload")) return "Team Workload";
            if (CurrentRoute.EndsWith("/triage")) return "Bug Triage";
            if (CurrentRoute.EndsWith("/verify")) return "Fixes to Verify";
            if (CurrentRoute.EndsWith("/my-work")) return "My Assignments";
            if (CurrentRoute.EndsWith("/my-bugs")) return "Bugs to Fix";
            if (CurrentRoute.EndsWith("/reported-bugs")) return "Bugs I Reported";
            if (CurrentRoute.EndsWith("/deliverables")) return "Milestones & Delivery";
            if (CurrentRoute.EndsWith("/progress")) return "Progress Report";
            if (CurrentRoute.Contains("/capabilities/")) return "Advanced Capabilities";
            if (CurrentRoute.StartsWith("projects/")) return "Overview";

            return "WorkSync";
        }
    }

    protected override async Task OnInitializedAsync()
    {
        Auth.OnChange += HandleStateChanged;
        ProjectContext.OnChange += HandleStateChanged;
        Navigation.LocationChanged += HandleLocationChanged;

        await Auth.InitializeAsync();

        if (Auth.IsActiveUser && ProjectContext.AccessibleProjects.Count == 0)
        {
            var res = await ProjectService.GetMyProjectsAsync();

            if (res.IsSuccess && res.Data is not null)
                ProjectContext.SetAccessibleProjects(res.Data);
        }
    }

    private void ToggleDrawer() => _drawerOpen = !_drawerOpen;

    private void HandleStateChanged() =>
        _ = InvokeAsync(StateHasChanged);

    private void HandleLocationChanged(
        object? sender,
        LocationChangedEventArgs e) =>
        _ = InvokeAsync(StateHasChanged);

    private async Task OnProjectChangedAsync(ChangeEventArgs e)
    {
        var projectId = e.Value?.ToString();

        if (string.IsNullOrWhiteSpace(projectId))
            return;

        var detailTask = ProjectService.GetProjectAsync(projectId);
        var permTask = ProjectService.GetMyPermissionsAsync(projectId);
        var configTask = ProjectService.GetConfigurationAsync(projectId);
        var prefTask = ProjectService.GetMyPreferencesAsync(projectId);

        await Task.WhenAll(detailTask, permTask, configTask, prefTask);

        if (detailTask.Result.IsSuccess && detailTask.Result.Data is not null
            && permTask.Result.IsSuccess && permTask.Result.Data is not null
            && configTask.Result.IsSuccess && configTask.Result.Data is not null)
        {
            ProjectContext.SetActiveProject(
                detailTask.Result.Data,
                permTask.Result.Data,
                configTask.Result.Data,
                prefTask.Result.Data);

            Navigation.NavigateTo($"/projects/{projectId}/overview");
        }
    }

    private Task OpenLogoutDialogAsync() =>
        DialogService.ShowLogoutConfirmAsync();

    private static string ProjectMonogram(string name) =>
        string.IsNullOrWhiteSpace(name)
            ? "P"
            : char.ToUpperInvariant(name.Trim()[0]).ToString();

    public void Dispose()
    {
        Auth.OnChange -= HandleStateChanged;
        ProjectContext.OnChange -= HandleStateChanged;
        Navigation.LocationChanged -= HandleLocationChanged;
    }
}
