
using Microsoft.AspNetCore.Components;
using MudBlazor;

namespace Worksync.Frontend.Pages;
using Worksync.Frontend.Components.Layout;

public partial class PendingApproval
{
    private bool _isRefreshing;
    private string? _infoMessage;
    private string? _errorMessage;

    private string CurrentStatus =>
        Auth.User?.Status ?? "PENDING_APPROVAL";

    private Color StatusColor => CurrentStatus switch
    {
        "ACTIVE" => Color.Success,
        "REJECTED" or "BLOCKED" => Color.Error,
        _ => Color.Warning
    };

    protected override void OnInitialized()
    {
        if (Auth.IsActiveUser)
        {
            Navigation.NavigateTo("/dashboard", replace: true);
        }
    }

    private async Task RefreshStatusAsync()
    {
        if (_isRefreshing)
            return;

        _isRefreshing = true;
        _infoMessage = null;
        _errorMessage = null;

        try
        {
            var result = await UserApi.GetMyProfileAsync();

            if (!result.IsSuccess || result.Data is null)
            {
                _errorMessage = result.ErrorMessage
                    ?? "Could not retrieve your current account status.";

                return;
            }

            await Auth.UpdateUserAsync(result.Data);

            switch (result.Data.Status)
            {
                case "ACTIVE":
                    Snackbar.Add(
                        "Your account has been approved!",
                        Severity.Success);

                    Navigation.NavigateTo("/dashboard", replace: true);
                    break;

                case "PENDING_APPROVAL":
                    _infoMessage =
                        "Status checked: your account is still awaiting administrator approval.";
                    break;

                case "REJECTED":
                case "BLOCKED":
                    // The UI automatically displays the updated status alert.
                    break;

                default:
                    _infoMessage = $"Current status: {result.Data.Status}";
                    break;
            }
        }
        finally
        {
            _isRefreshing = false;
        }
    }

    private async Task OpenLogoutDialogAsync()
    {
        var options = new DialogOptions
        {
            NoHeader = true,
            MaxWidth = MaxWidth.ExtraSmall,
            FullWidth = true,
            CloseOnEscapeKey = true,
            BackdropClick = true,
            BackgroundClass = "logout-dialog-backdrop"
        };

        await DialogService.ShowAsync<LogoutConfirmDialog>(
            string.Empty,
            options);
    }
}
