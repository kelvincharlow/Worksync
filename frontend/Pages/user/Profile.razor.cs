using Microsoft.AspNetCore.Components;
using Microsoft.AspNetCore.Components.Forms;
using MudBlazor;
using Worksync.Frontend.Models.Shared;
using Worksync.Frontend.Models.Users;
using Worksync.Frontend.Services;

namespace Worksync.Frontend.Pages;

public partial class Profile
{
    [Inject] private AuthState Auth { get; set; } = default!;
    [Inject] private UserApiService UserApi { get; set; } = default!;
    [Inject] private ISnackbar Snackbar { get; set; } = default!;

    private UserRecord? _profile;
    private UpdateMyProfileRequest _model = new();
    private EditContext _editContext = default!;
    private ValidationMessageStore _messageStore = default!;

    private bool _isEditing;
    private bool _isSaving;
    private string? _errorMessage;

    protected override void OnInitialized()
    {
        // Populate immediately from AuthState so the page opens with zero loading delay
        _profile = Auth.User;
        InitForm(_profile);
    }

    protected override async Task OnInitializedAsync()
    {
        await RefreshProfileSilentlyAsync();
    }

    private void InitForm(UserRecord? source)
    {
        _model = new UpdateMyProfileRequest
        {
            FirstName = source?.FirstName ?? string.Empty,
            LastName = source?.LastName ?? string.Empty,
            PhoneNumber = source?.PhoneNumber ?? string.Empty
        };

        _editContext = new EditContext(_model);
        _messageStore = new ValidationMessageStore(_editContext);
        _editContext.OnFieldChanged += (_, args) => _messageStore.Clear(args.FieldIdentifier);
    }
    private string GetFullName()
{
    var fullName = $"{_profile?.FirstName} {_profile?.LastName}".Trim();

    return string.IsNullOrWhiteSpace(fullName)
        ? "WorkSync User"
        : fullName;
}

private string GetInitials()
{
    var firstName = _profile?.FirstName?.Trim();
    var lastName = _profile?.LastName?.Trim();

    var firstInitial = !string.IsNullOrEmpty(firstName)
        ? firstName[0].ToString()
        : "";

    var lastInitial = !string.IsNullOrEmpty(lastName)
        ? lastName[0].ToString()
        : "";

    var initials = $"{firstInitial}{lastInitial}".ToUpperInvariant();

    return string.IsNullOrWhiteSpace(initials) ? "W" : initials;
}

    private async Task RefreshProfileSilentlyAsync()
    {
        var result = await UserApi.GetMyProfileAsync();
        if (result.IsSuccess && result.Data is not null)
        {
            _profile = result.Data;
            await Auth.UpdateUserAsync(result.Data);

            // Do not overwrite fields if the user already clicked Edit profile
            if (!_isEditing)
            {
                InitForm(_profile);
            }
        }
        else if (result.StatusCode != 401 && _profile is null)
        {
            _errorMessage = result.ErrorMessage ?? "Could not load your profile details.";
        }
    }
    
    private void StartEditing()
    {
        _errorMessage = null;
        InitForm(_profile);
        _isEditing = true;
    }

    private void CancelEditing()
    {
        _errorMessage = null;
        _isEditing = false;
        InitForm(_profile);
    }

    private async Task SaveProfileAsync()
    {
        if (_isSaving) return;

        _isSaving = true;
        _errorMessage = null;
        _messageStore.Clear();

        try
        {
            var result = await UserApi.UpdateMyProfileAsync(_model);

            if (!result.IsSuccess || result.Data is null)
            {
                var unmapped = _editContext.ApplyFieldErrors(_messageStore, _model, result.FieldErrors);
                _errorMessage = unmapped.Count > 0
                    ? string.Join(" ", unmapped)
                    : (result.ErrorMessage ?? "Could not update your profile.");
                return;
            }

            _profile = result.Data;
            await Auth.UpdateUserAsync(result.Data);
            InitForm(_profile);
            _isEditing = false;

            Snackbar.Add("Profile updated successfully.", Severity.Success);
        }
        finally
        {
            _isSaving = false;
        }
    }
}