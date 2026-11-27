
using Microsoft.AspNetCore.Components;
using Worksync.Frontend.Models.Shared;
using Worksync.Frontend.Services;

namespace Worksync.Frontend.Pages;

public partial class Dashboard
{
    private UserRecord? _profile;

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
        if (_isLoading)
            return;

        _isLoading = true;
        _errorMessage = null;

        try
        {
            var profileTask = UserApi.GetMyProfileAsync();

            if (Auth.IsActiveAdmin)
            {
                var pendingTask = UserApi.GetAdminUsersAsync(
                    "PENDING_APPROVAL", page: 0, size: 1);

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

    private static string GetInitials(UserRecord user)
    {
        var f = user.FirstName?.Trim();
        var l = user.LastName?.Trim();

        if (!string.IsNullOrEmpty(f) &&
            !string.IsNullOrEmpty(l))
        {
            return $"{char.ToUpperInvariant(f[0])}{char.ToUpperInvariant(l[0])}";
        }

        return !string.IsNullOrEmpty(user.Email)
            ? char.ToUpperInvariant(user.Email[0]).ToString()
            : "U";
    }

    private static string FormatStatus(string status) => status switch
    {
        "PENDING_APPROVAL" => "Pending",
        "ACTIVE" => "Active",
        "REJECTED" => "Rejected",
        "BLOCKED" => "Blocked",
        _ => status
    };

    private static string FormatDate(DateTimeOffset? date) =>
        date?.ToLocalTime().ToString("d MMM yyyy") ?? "—";
}
