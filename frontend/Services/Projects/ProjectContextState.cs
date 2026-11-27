using Worksync.Frontend.Models.Projects;

namespace Worksync.Frontend.Services.Projects;

public class ProjectContextState : IDisposable
{
    private readonly AuthState _authState;

    public ProjectContextState(AuthState authState)
    {
        _authState = authState;
        _authState.OnChange += HandleAuthChanged;
    }

    public IReadOnlyList<ProjectSummary> AccessibleProjects { get; private set; } =
        Array.Empty<ProjectSummary>();

    public ProjectDetail? ActiveProject { get; private set; }
    public EffectiveProjectPermissions? ActivePermissions { get; private set; }
    public ProjectConfigurationModel? ActiveConfiguration { get; private set; }
    public ProjectPreferenceModel? ActivePreference { get; private set; }

    public event Action? OnChange;

    public bool HasActiveProject => ActiveProject is not null;
    public bool IsProjectArchived => ActiveProject?.Status == ProjectStatus.ARCHIVED;

    public bool CanCreateProjects =>
    _authState.IsActiveUser
    && !_authState.IsActiveAdmin
    && (_authState.User?.Permissions.Contains(
        "PROJECT_CREATE",
        StringComparer.OrdinalIgnoreCase) == true);

    public EffectiveWorkspaceRole WorkspaceRole
    {
        get
        {
            if (ActivePermissions is null)
                return EffectiveWorkspaceRole.None;

            return ActivePermissions.Role switch
            {
                ProjectRole.MANAGER => ActivePermissions.IsOwner
                    ? EffectiveWorkspaceRole.Owner
                    : EffectiveWorkspaceRole.Manager,

                ProjectRole.VIEWER => EffectiveWorkspaceRole.Viewer,

                ProjectRole.MEMBER => ActivePermissions.Responsibility switch
                {
                    ProjectResponsibility.TECHNICAL_LEAD =>
                        EffectiveWorkspaceRole.TechnicalLead,

                    ProjectResponsibility.QA_TESTER =>
                        EffectiveWorkspaceRole.QaTester,

                    _ => EffectiveWorkspaceRole.Developer
                },

                _ => EffectiveWorkspaceRole.None
            };
        }
    }

    public bool IsManagerOrOwner =>
        WorkspaceRole is EffectiveWorkspaceRole.Owner
            or EffectiveWorkspaceRole.Manager;

    public bool IsOwner =>
        WorkspaceRole == EffectiveWorkspaceRole.Owner && !IsProjectArchived;

    public bool Can(ProjectAction action) =>
        ActivePermissions?.AllowedActions.Contains(action) == true;

    public bool CanMutate(ProjectAction action) =>
        _authState.IsActiveUser && !IsProjectArchived && Can(action);

    public void SetAccessibleProjects(IEnumerable<ProjectSummary> projects)
    {
        AccessibleProjects = projects.ToList();
        OnChange?.Invoke();
    }

    public void SetActiveProject(
        ProjectDetail project,
        EffectiveProjectPermissions permissions,
        ProjectConfigurationModel configuration,
        ProjectPreferenceModel? preference = null)
    {
        ActiveProject = project;
        ActivePermissions = permissions;
        ActiveConfiguration = configuration;
        ActivePreference = preference
            ?? new ProjectPreferenceModel { ProjectId = project.Id };

        OnChange?.Invoke();
    }

    public void UpdateConfiguration(ProjectConfigurationModel configuration)
    {
        ActiveConfiguration = configuration;
        OnChange?.Invoke();
    }

    public void UpdatePreference(ProjectPreferenceModel preference)
    {
        ActivePreference = preference;
        OnChange?.Invoke();
    }

    public void ClearActiveProject()
    {
        ActiveProject = null;
        ActivePermissions = null;
        ActiveConfiguration = null;
        ActivePreference = null;

        OnChange?.Invoke();
    }

    private void HandleAuthChanged()
    {
        if (!_authState.IsAuthenticated)
        {
            AccessibleProjects = Array.Empty<ProjectSummary>();
            ActiveProject = null;
            ActivePermissions = null;
            ActiveConfiguration = null;
            ActivePreference = null;

            OnChange?.Invoke();
        }
    }

    public void Dispose()
    {
        _authState.OnChange -= HandleAuthChanged;
    }
}