
using Microsoft.AspNetCore.Components;
using MudBlazor;

namespace Worksync.Frontend.Components.Layout;

public partial class LogoutConfirmDialog
{
    [CascadingParameter]
    private IMudDialogInstance MudDialog { get; set; } = default!;

    private bool _isLoggingOut;

    private void Cancel()
    {
        if (!_isLoggingOut)
            MudDialog.Cancel();
    }

    private async Task ConfirmLogoutAsync()
    {
        if (_isLoggingOut) return;

        _isLoggingOut = true;

        try
        {
            var result = await SessionService.LogoutAsync();

            MudDialog.Close(DialogResult.Ok(true));

            if (!result.IsSuccess)
            {
                Snackbar.Add(
                    result.ErrorMessage ?? "Could not notify server, but your local session was cleared.",
                    Severity.Warning);
            }
            else
            {
                Snackbar.Add("Logged out.", Severity.Info);
            }

            Navigation.NavigateTo("/login", replace: true);
        }
        finally
        {
            _isLoggingOut = false;
        }
    }
}
