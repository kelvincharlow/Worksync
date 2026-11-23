using Microsoft.Extensions.Logging;

namespace Worksync.Frontend.Services;

public class AuthSessionService
{
    private readonly AuthApiService _authApi;
    private readonly AuthState _authState;
    private readonly ILogger<AuthSessionService> _logger;
    private readonly SemaphoreSlim _refreshLock = new(1, 1);

    public AuthSessionService(
        AuthApiService authApi,
        AuthState authState,
        ILogger<AuthSessionService> logger)
    {
        _authApi = authApi;
        _authState = authState;
        _logger = logger;
    }

    /// <summary>
    /// Refreshes the access token using the stored refresh token.
    /// Prevents concurrent requests from sending duplicate refresh calls.
    /// </summary>
    public async Task<bool> TryRefreshAsync(string? failedAccessToken = null)
    {
        await _authState.InitializeAsync();

        if (!_authState.HasRefreshToken)
        {
            await _authState.ClearAsync();
            return false;
        }

        await _refreshLock.WaitAsync();
        try
        {
            // Another concurrent request may have already rotated the token while we waited
            if (!string.IsNullOrWhiteSpace(failedAccessToken)
                && !string.Equals(_authState.AccessToken, failedAccessToken, StringComparison.Ordinal)
                && _authState.IsAccessTokenValid)
            {
                return true;
            }

            if (string.IsNullOrWhiteSpace(failedAccessToken) && _authState.IsAccessTokenValid)
            {
                return true;
            }

            if (!_authState.HasRefreshToken)
            {
                await _authState.ClearAsync();
                return false;
            }

            var result = await _authApi.RefreshAsync(_authState.RefreshToken!);

            if (!result.IsSuccess || result.Data is null || string.IsNullOrWhiteSpace(result.Data.AccessToken))
            {
                _logger.LogWarning("Token refresh failed with status {StatusCode}; clearing session.", result.StatusCode);
                await _authState.ClearAsync();
                return false;
            }

            var rotatedRefreshToken = string.IsNullOrWhiteSpace(result.Data.RefreshToken)
                ? null
                : result.Data.RefreshToken;

            var user = string.IsNullOrWhiteSpace(result.Data.User?.Id)
                ? (_authState.User ?? result.Data.User!)
                : result.Data.User;

            await _authState.SetSessionAsync(
                result.Data.AccessToken,
                rotatedRefreshToken,
                result.Data.ExpiresIn,
                user);

            return true;
        }
        finally
        {
            _refreshLock.Release();
        }
    }

    /// <summary>
    /// Calls POST /auth/logout if an access token is present, and always clears local auth state.
    /// </summary>
    public async Task<ApiResult> LogoutAsync()
{
    await _authState.InitializeAsync();
    var accessToken = _authState.AccessToken;

    // Clear local state & browser storage immediately before calling the server
    await _authState.ClearAsync();

    if (string.IsNullOrWhiteSpace(accessToken))
    {
        return ApiResult.Ok();
    }

    return await _authApi.LogoutAsync(accessToken);
}
}
