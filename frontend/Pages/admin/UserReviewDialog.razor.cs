
using Microsoft.AspNetCore.Components;
using MudBlazor;
using Worksync.Frontend.Models.Shared;
using Worksync.Frontend.Services;

namespace Worksync.Frontend.Components.Admin;

public partial class UserReviewDialog
{
    [CascadingParameter]
    private IMudDialogInstance MudDialog { get; set; } = default!;

    [Parameter]
    public string UserId { get; set; } = string.Empty;

    private UserRecord? _user;
    private bool _isLoading = true;
    private bool _isSubmitting;
    private string? _loadError;
    private string? _actionError;
    private string? _pendingAction;
    private string _reason = string.Empty;

    private bool IsDestructiveAction =>
        _pendingAction is "reject" or "block";

    private string ActionHeading => _pendingAction switch
    {
        "approve" => "Approve registration",
        "reject" => "Reject registration",
        "block" => "Block account",
        "unblock" => "Unblock account",
        _ => "Confirm action"
    };

    private string ActionExplanation => _pendingAction switch
    {
        "approve" => "The user will become ACTIVE and gain access according to their role.",
        "reject" => "The registration will become REJECTED. Rejected accounts cannot sign in.",
        "block" => "The account will become BLOCKED and immediately lose access to protected endpoints.",
        "unblock" => "The blocked account will return to ACTIVE status.",
        _ => string.Empty
    };

    protected override async Task OnInitializedAsync()
    {
        await LoadUserAsync();
    }

    private async Task LoadUserAsync()
    {
        _isLoading = true;
        _loadError = null;

        try
        {
            var result = await UserApi.GetAdminUserByIdAsync(UserId);

            if (result.IsSuccess && result.Data is not null)
            {
                _user = result.Data;
            }
            else
            {
                _loadError = result.StatusCode switch
                {
                    403 => "You do not have administrator permission to view this account.",
                    404 => "This user account was not found.",
                    _ => result.ErrorMessage ?? "Could not load user details."
                };
            }
        }
        finally
        {
            _isLoading = false;
        }
    }

    private void StartAction(string action)
    {
        _actionError = null;
        _reason = string.Empty;
        _pendingAction = action;
    }

    private void BackToDetails()
    {
        if (_isSubmitting) return;

        _actionError = null;
        _pendingAction = null;
    }

    private void Close()
    {
        if (!_isSubmitting)
            MudDialog.Cancel();
    }

    private async Task ApplyActionAsync()
    {
        if (_isSubmitting || _user is null || _pendingAction is null)
            return;

        if (_reason.Trim().Length > 500)
        {
            _actionError = "Reason must not exceed 500 characters.";
            return;
        }

        _isSubmitting = true;
        _actionError = null;

        try
        {
            var result = _pendingAction switch
            {
                "approve" => await UserApi.ApproveUserAsync(_user.Id),

                "reject" => await UserApi.RejectUserAsync(_user.Id, _reason),

                "block" => await UserApi.ChangeUserStatusAsync(
                    _user.Id, "BLOCKED", _reason),

                "unblock" => await UserApi.ChangeUserStatusAsync(
                    _user.Id, "ACTIVE", _reason),

                _ => ApiResult<UserRecord>.Fail("Unsupported action.")
            };

            if (result.IsSuccess && result.Data is not null)
            {
                Snackbar.Add(
                    $"{_user.FirstName} {_user.LastName}: status updated to {FormatStatus(result.Data.Status)}.",
                    Severity.Success);

                MudDialog.Close(DialogResult.Ok(result.Data));
                return;
            }

            _actionError = result.StatusCode switch
            {
                403 => result.ErrorMessage ??
                       "Forbidden: ACTIVE ADMIN access is required for this operation.",

                404 => result.ErrorMessage ?? "User account not found.",

                409 => result.ErrorMessage ??
                       "This status change is not permitted (invalid transition or protected admin account).",

                _ => result.ErrorMessage ??
                     "Could not apply account status change."
            };
        }
        finally
        {
            _isSubmitting = false;
        }
    }

    private static string GetInitials(UserRecord user)
    {
        var f = user.FirstName?.Trim();
        var l = user.LastName?.Trim();

        if (!string.IsNullOrEmpty(f) && !string.IsNullOrEmpty(l))
            return $"{char.ToUpperInvariant(f[0])}{char.ToUpperInvariant(l[0])}";

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
