using System.Text.Json;
using Microsoft.JSInterop;
using Worksync.Frontend.Models.Shared;

namespace Worksync.Frontend.Services;

public class AuthState
{
    private const string StorageKey = "worksync.auth.session";
    private readonly IJSRuntime _js;
    private readonly SemaphoreSlim _initLock = new(1, 1);
    private bool _initialized;

    public AuthState(IJSRuntime js)
    {
        _js = js;
    }

    public string? AccessToken { get; private set; }
    public string? RefreshToken { get; private set; }
    public DateTimeOffset? ExpiresAt { get; private set; }
    public UserRecord? User { get; private set; }

    public event Action? OnChange;

    public bool HasRefreshToken => !string.IsNullOrWhiteSpace(RefreshToken);

   public bool IsAccessTokenValid =>
    !string.IsNullOrWhiteSpace(AccessToken)
    && ExpiresAt.HasValue
    && ExpiresAt.Value > DateTimeOffset.UtcNow.AddSeconds(5);    public bool IsAuthenticated =>
        (!string.IsNullOrWhiteSpace(AccessToken) && ExpiresAt > DateTimeOffset.UtcNow)
        || HasRefreshToken;

    public bool IsActiveUser => string.Equals(User?.Status, "ACTIVE", StringComparison.OrdinalIgnoreCase);

    public bool IsActiveAdmin =>
        IsActiveUser && string.Equals(User?.Role, "ADMIN", StringComparison.OrdinalIgnoreCase);

    public async Task InitializeAsync()
    {
        if (_initialized) return;

        await _initLock.WaitAsync();
        try
        {
            if (_initialized) return;

            var json = await _js.InvokeAsync<string?>("sessionStorage.getItem", StorageKey);
            if (!string.IsNullOrWhiteSpace(json))
            {
                var snapshot = JsonSerializer.Deserialize<SessionSnapshot>(json);
                if (snapshot is not null && !string.IsNullOrWhiteSpace(snapshot.AccessToken))
                {
                    AccessToken = snapshot.AccessToken;
                    RefreshToken = snapshot.RefreshToken;
                    ExpiresAt = snapshot.ExpiresAt;
                    User = snapshot.User;
                }
            }
        }
        catch
        {
            // Corrupted storage or unavailable JS context: reset cleanly
            await ClearStorageSafelyAsync();
        }
        finally
        {
            _initialized = true;
            _initLock.Release();
        }
    }

    public async Task SetSessionAsync(
        string accessToken,
        string? refreshToken,
        int expiresInSeconds,
        UserRecord user)
    {
        AccessToken = accessToken;
        RefreshToken = string.IsNullOrWhiteSpace(refreshToken) ? null : refreshToken;
        ExpiresAt = DateTimeOffset.UtcNow.AddSeconds(Math.Max(expiresInSeconds, 1));
        User = user;
        _initialized = true;

        await PersistAsync();
        OnChange?.Invoke();
    }

    public async Task UpdateUserAsync(UserRecord user)
    {
        User = user;
        await PersistAsync();
        OnChange?.Invoke();
    }

    public async Task ClearAsync()
    {
        AccessToken = null;
        RefreshToken = null;
        ExpiresAt = null;
        User = null;
        _initialized = true;

        await ClearStorageSafelyAsync();
        OnChange?.Invoke();
    }

    private async Task PersistAsync()
    {
        try
        {
            var snapshot = new SessionSnapshot(AccessToken, RefreshToken, ExpiresAt, User);
            var json = JsonSerializer.Serialize(snapshot);
            await _js.InvokeVoidAsync("sessionStorage.setItem", StorageKey, json);
        }
        catch
        {
            // Ignore storage write failure; in-memory state remains active
        }
    }

    private async Task ClearStorageSafelyAsync()
{
    try
    {
        await _js.InvokeVoidAsync("sessionStorage.removeItem", StorageKey);
        await _js.InvokeVoidAsync("sessionStorage.removeItem", "accessToken");
        await _js.InvokeVoidAsync("sessionStorage.removeItem", "refreshToken");
        await _js.InvokeVoidAsync("localStorage.removeItem", "accessToken");
        await _js.InvokeVoidAsync("localStorage.removeItem", "refreshToken");
    }
    catch
        {
            // Ignore storage clear failure
        }
    }

    private sealed record SessionSnapshot(
        string? AccessToken,
        string? RefreshToken,
        DateTimeOffset? ExpiresAt,
        UserRecord? User);
}