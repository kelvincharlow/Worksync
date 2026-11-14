namespace Worksync.Frontend.Services;
using Worksync.Frontend.Models.Shared;
public class AuthState
{
    public string? AccessToken { get; private set; }
    public DateTimeOffset? ExpiresAt { get; private set; }
    public UserRecord? User { get; private set; }

    public bool IsAuthenticated =>
        !string.IsNullOrEmpty(AccessToken) && ExpiresAt > DateTimeOffset.UtcNow;

    public void SetSession(string token, int expiresInSeconds, UserRecord user)
    {
        AccessToken = token;
        ExpiresAt = DateTimeOffset.UtcNow.AddSeconds(expiresInSeconds);
        User = user;
    }

    public void Clear()
    {
        AccessToken = null;
        ExpiresAt = null;
        User = null;
    }
}

