
using MudBlazor;
using Worksync.Frontend.Models.Shared;
using Worksync.Frontend.Components.Admin;

namespace Worksync.Frontend.Pages.Admin;

public partial class Users
{
    private const int PageSize = 10;

    private List<UserRecord> _users = new();
    private string _statusFilter = "ALL";
    private string _search = string.Empty;
    private int _page;
    private int _totalPages = 1;
    private long _totalElements;
    private bool _isLoading = true;
    private string? _errorMessage;

    private List<UserRecord> FilteredUsers =>
        string.IsNullOrWhiteSpace(_search)
            ? _users
            : _users.Where(u =>
                $"{u.FirstName} {u.LastName} {u.Email} {u.PhoneNumber}"
                    .Contains(_search.Trim(), StringComparison.OrdinalIgnoreCase))
                .ToList();

    private long RangeStart =>
        _totalElements == 0 ? 0 : (_page * PageSize) + 1;

    private long RangeEnd =>
        Math.Min(_totalElements, (_page + 1) * (long)PageSize);

    protected override async Task OnInitializedAsync()
    {
        await LoadUsersAsync();
    }

    private async Task OnStatusFilterChangedAsync(string newStatus)
    {
        _statusFilter = newStatus;
        _page = 0;
        await LoadUsersAsync();
    }

    private async Task LoadUsersAsync()
    {
        _isLoading = true;
        _errorMessage = null;

        try
        {
            var result = await UserApi.GetAdminUsersAsync(
                _statusFilter, _page, PageSize);

            if (result.IsSuccess && result.Data is not null)
            {
                _users = result.Data.Content;
                _totalElements = result.Data.TotalElements;
                _totalPages = result.Data.TotalPages;
            }
            else
            {
                _users.Clear();
                _errorMessage = result.ErrorMessage ?? "Could not load users.";
            }
        }
        finally
        {
            _isLoading = false;
        }
    }

    private async Task PreviousPageAsync()
    {
        if (_page > 0)
        {
            _page--;
            await LoadUsersAsync();
        }
    }

    private async Task NextPageAsync()
    {
        if (_page < _totalPages - 1)
        {
            _page++;
            await LoadUsersAsync();
        }
    }

    private async Task OpenReviewAsync(string userId)
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
        {
            await LoadUsersAsync();
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
}
