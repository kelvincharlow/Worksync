
using Microsoft.AspNetCore.Components;
using Microsoft.AspNetCore.Components.Forms;
using Worksync.Frontend.Models.Auth;
using Worksync.Frontend.Services;

namespace Worksync.Frontend.Pages.password;

public partial class ForgotPassword
{
    private readonly PasswordResetRequest _model = new();

    private EditContext _editContext = default!;
    private ValidationMessageStore _messageStore = default!;

    private bool _isLoading;
    private string? _errorMessage;

    protected override void OnInitialized()
    {
        _editContext = new EditContext(_model);

        _messageStore = new ValidationMessageStore(_editContext);

        _editContext.OnFieldChanged += (_, args) =>
            _messageStore.Clear(args.FieldIdentifier);
    }

    private async Task HandleSubmitAsync()
    {
        if (_isLoading)
            return;

        _isLoading = true;
        _errorMessage = null;

        _messageStore.Clear();

        try
        {
            var result = await AuthApi.RequestPasswordResetAsync(_model);

            if (result.IsSuccess)
            {
                // Anti-enumeration:
                // Do not confirm whether the email exists.
                // The sent=true parameter starts the reset cooldown.
                Navigation.NavigateTo(
                    $"/reset-password?email={Uri.EscapeDataString(_model.Email.Trim())}&sent=true");

                return;
            }

            if (result.StatusCode == 429)
            {
                _errorMessage = result.ErrorMessage
                    ?? "Too many reset requests. Please wait before requesting another code.";

                return;
            }

            var unmapped = _editContext.ApplyFieldErrors(
                _messageStore,
                _model,
                result.FieldErrors);

            _errorMessage = unmapped.Count > 0
                ? string.Join(" ", unmapped)
                : result.ErrorMessage
                    ?? "Something went wrong. Please try again.";
        }
        finally
        {
            _isLoading = false;
        }
    }
}
