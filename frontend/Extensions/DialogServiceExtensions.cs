using MudBlazor;
using Worksync.Frontend.Components.Admin;
using Worksync.Frontend.Components.Layout;

namespace Worksync.Frontend.Extensions;

public static class DialogServiceExtensions
{
    private static DialogOptions CreateBlurredOptions(MaxWidth maxWidth) => new()
    {
        NoHeader = true,
        MaxWidth = maxWidth,
        FullWidth = true,
        CloseOnEscapeKey = true,
        BackdropClick = true,
        BackgroundClass = "logout-dialog-backdrop"
    };

    public static Task ShowLogoutConfirmAsync(this IDialogService dialogService) =>
        dialogService.ShowAsync<LogoutConfirmDialog>(
            string.Empty,
            CreateBlurredOptions(MaxWidth.ExtraSmall));

    public static async Task<bool> ShowUserReviewAsync(
        this IDialogService dialogService,
        string userId)
    {
        var parameters = new DialogParameters<UserReviewDialog>
        {
            { x => x.UserId, userId }
        };

        var dialog = await dialogService.ShowAsync<UserReviewDialog>(
            string.Empty,
            parameters,
            CreateBlurredOptions(MaxWidth.Small));

        var result = await dialog.Result;

        return result is not null && !result.Canceled;
    }
}