using Worksync.Frontend.Models.WorkItems;

namespace Worksync.Frontend.Services.Projects;

public interface IWorkItemService
{
    Task<ApiResult<List<WorkItemRecord>>> GetProjectWorkItemsAsync(string projectId);
    Task<ApiResult<List<WorkItemRecord>>> GetMyAssignedWorkAsync();
    Task<ApiResult<WorkItemSummary>> GetProjectSummaryAsync(string projectId);

    Task<ApiResult<WorkItemRecord>> CreateWorkItemAsync(string projectId, CreateWorkItemRequest request);
    Task<ApiResult<WorkItemRecord>> UpdateWorkItemStatusAsync(string projectId, string workItemId, WorkItemStatus status);
    Task<ApiResult<WorkItemRecord>> AssignWorkItemAsync(string projectId, string workItemId, string? assigneeId);
    Task<ApiResult<WorkItemRecord>> ToggleSubtaskAsync(string projectId, string workItemId, string subtaskId, bool isDone);

    Task<ApiResult<WorkItemRecord>> ReportBugAsync(string projectId, ReportBugRequest request);
    Task<ApiResult<WorkItemRecord>> TriageBugAsync(string projectId, string bugId, TriageBugRequest request);
    Task<ApiResult<WorkItemRecord>> SubmitBugFixAsync(string projectId, string bugId);
    Task<ApiResult<WorkItemRecord>> VerifyBugAsync(string projectId, string bugId, VerifyBugRequest request);
}
