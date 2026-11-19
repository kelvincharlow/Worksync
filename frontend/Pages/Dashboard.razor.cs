
using Microsoft.AspNetCore.Components;
using MudBlazor;
using Worksync.Frontend.Services;
using Worksync.Frontend.Models.Shared;
using Worksync.Frontend.Pages.admin;

namespace Worksync.Frontend.Pages;

public partial class Dashboard
{
    [Inject] public AuthState Auth { get; set; } = default!;
    [Inject] public UserApiService UserApi { get; set; } = default!;
    [Inject] public IDialogService DialogService { get; set; } = default!;

    private UserRecord? _profile;
    private List<UserRecord> _pendingUsers = new();

    private long _pendingCount;
    private long _activeCount;
    private long _rejectedCount;
    private long _blockedCount;

    private bool _isLoading;
    private string? _errorMessage;

    private UserRecord? CurrentUser => _profile ?? Auth.User;

    private string DisplayFirstName =>
        string.IsNullOrWhiteSpace(CurrentUser?.FirstName)
            ? "back"
            : CurrentUser!.FirstName;

    protected override async Task OnInitializedAsync()
    {
        _profile = Auth.User;
        await LoadOverviewAsync();
    }

    private async Task LoadOverviewAsync()
    {
        if (_isLoading) return;

        _isLoading = true;
        _errorMessage = null;

        try
        {
            var profileTask = UserApi.GetMyProfileAsync();

            if (Auth.IsActiveAdmin)
            {
                var pendingTask = UserApi.GetAdminUsersAsync(
                    "PENDING_APPROVAL", page: 0, size: 5);

                var activeTask = UserApi.GetAdminUsersAsync(
                    "ACTIVE", page: 0, size: 1);

                var rejectedTask = UserApi.GetAdminUsersAsync(
                    "REJECTED", page: 0, size: 1);

                var blockedTask = UserApi.GetAdminUsersAsync(
                    "BLOCKED", page: 0, size: 1);

                await Task.WhenAll(
                    profileTask,
                    pendingTask,
                    activeTask,
                    rejectedTask,
                    blockedTask);

                var pendingRes = pendingTask.Result;
                var activeRes = activeTask.Result;
                var rejectedRes = rejectedTask.Result;
                var blockedRes = blockedTask.Result;

                if (pendingRes.IsSuccess && pendingRes.Data is not null)
                {
                    _pendingUsers = pendingRes.Data.Content;
                    _pendingCount = pendingRes.Data.TotalElements;
                }
                else
                {
                    _errorMessage = pendingRes.ErrorMessage
                        ?? "Could not load overview metrics.";
                }

                if (activeRes.IsSuccess && activeRes.Data is not null)
                    _activeCount = activeRes.Data.TotalElements;

                if (rejectedRes.IsSuccess && rejectedRes.Data is not null)
                    _rejectedCount = rejectedRes.Data.TotalElements;

                if (blockedRes.IsSuccess && blockedRes.Data is not null)
                    _blockedCount = blockedRes.Data.TotalElements;
            }
            else
            {
                await profileTask;
            }

            if (profileTask.Result.IsSuccess &&
                profileTask.Result.Data is not null)
            {
                _profile = profileTask.Result.Data;
                await Auth.UpdateUserAsync(profileTask.Result.Data);
            }
            else if (!Auth.IsActiveAdmin &&
                     profileTask.Result.StatusCode != 401)
            {
                _errorMessage = profileTask.Result.ErrorMessage
                    ?? "Could not refresh profile details.";
            }
        }
        finally
        {
            _isLoading = false;
        }
    }

    private async Task OpenReviewDialogAsync(string userId)
    {
        var parameters = new DialogParameters<UserReviewDialog>
        {
            { x => x.UserId, userId }
        };

        var options = new DialogOptions
        {
            NoHeader = true,
            MaxWidth = MaxWidth.Small,
            FullWidth = true,
            CloseOnEscapeKey = true,
            BackdropClick = true,
            BackgroundClass = "logout-dialog-backdrop"
        };

        var dialog = await DialogService.ShowAsync<UserReviewDialog>(
            string.Empty, parameters, options);

        var result = await dialog.Result;

        if (result is not null && !result.Canceled)
            await LoadOverviewAsync();
    }
}
