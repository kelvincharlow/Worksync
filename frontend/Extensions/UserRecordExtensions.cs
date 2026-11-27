using Worksync.Frontend.Models.Shared;

namespace Worksync.Frontend.Extensions;

public static class UserRecordExtensions
{
    public static string FullName(this UserRecord? user) =>
        user is null ? string.Empty : $"{user.FirstName} {user.LastName}".Trim();

    public static string Initials(this UserRecord? user)
    {
        var f = user?.FirstName?.Trim();
        var l = user?.LastName?.Trim();

        if (!string.IsNullOrEmpty(f) && !string.IsNullOrEmpty(l))
            return $"{char.ToUpperInvariant(f[0])}{char.ToUpperInvariant(l[0])}";

        var email = user?.Email?.Trim();

        return !string.IsNullOrEmpty(email)
            ? char.ToUpperInvariant(email[0]).ToString()
            : "U";
    }

    public static string StatusLabel(this string? status) => status switch
    {
        "PENDING_APPROVAL" => "Pending",
        "ACTIVE" => "Active",
        "REJECTED" => "Rejected",
        "BLOCKED" => "Blocked",
        _ => string.IsNullOrWhiteSpace(status) ? "Unknown" : status
    };

    public static string FormatShortDate(this DateTimeOffset? date) =>
        date?.ToLocalTime().ToString("d MMM yyyy") ?? "-";
}