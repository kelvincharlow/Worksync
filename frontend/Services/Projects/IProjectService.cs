using Worksync.Frontend.Models.Projects;
using Worksync.Frontend.Models.WorkItems;

namespace Worksync.Frontend.Services.Projects;

public interface IProjectService
{
    Task<ApiResult<List<ProjectSummary>>> GetMyProjectsAsync();
    Task<ApiResult<ProjectDetail>> GetProjectAsync(string projectId);
    Task<ApiResult<ProjectDetail>> CreateProjectAsync(CreateProjectRequest request);
    Task<ApiResult<ProjectDetail>> UpdateProjectAsync(string projectId, UpdateProjectRequest request);
    Task<ApiResult<ProjectDetail>> ToggleArchiveAsync(string projectId);

    Task<ApiResult<EffectiveProjectPermissions>> GetMyPermissionsAsync(string projectId);
    Task<ApiResult<ProjectConfigurationModel>> GetConfigurationAsync(string projectId);
    Task<ApiResult<ProjectConfigurationModel>> UpdateConfigurationAsync(string projectId, ProjectConfigurationModel config);

    Task<ApiResult<ProjectPreferenceModel>> GetMyPreferencesAsync(string projectId);
    Task<ApiResult<ProjectPreferenceModel>> UpdateMyPreferencesAsync(string projectId, ProjectPreferenceModel preference);

    Task<ApiResult<List<ProjectMemberRecord>>> GetMembersAsync(string projectId);
    Task<ApiResult<ProjectMemberRecord>> AddMemberAsync(string projectId, AddProjectMemberRequest request);
    Task<ApiResult<ProjectMemberRecord>> UpdateMemberAsync(string projectId, string userId, UpdateProjectMemberRequest request);
    Task<ApiResult> RemoveMemberAsync(string projectId, string userId);
    Task<ApiResult> TransferOwnershipAsync(string projectId, TransferOwnershipRequest request);

    Task<ApiResult<List<ProjectMilestoneModel>>> GetMilestonesAsync(string projectId);
    Task<ApiResult<ProjectMilestoneModel>> CreateMilestoneAsync(string projectId, CreateMilestoneRequest request);
    Task<ApiResult<ProjectMilestoneModel>> UpdateMilestoneAsync(string projectId, string milestoneId, UpdateMilestoneRequest request);

    Task<ApiResult<ClientDeliverySummary>> GetDeliverySummaryAsync(string projectId);
}