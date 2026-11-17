using System.Net.Http.Headers;
using System.Net.Http.Json;
using Microsoft.Extensions.Logging;
using Worksync.Frontend.Models.Auth;
using Worksync.Frontend.Models.Shared;

namespace Worksync.Frontend.Services;

public class AuthApiService
{
    private readonly HttpClient _http;
    private readonly ILogger<AuthApiService> _logger;
    private readonly AuthState _authState;

    public AuthApiService(
        HttpClient http,
        ILogger<AuthApiService> logger,
        AuthState authState)
    {
        _http = http;
        _logger = logger;
        _authState = authState;
    }

    public Task<ApiResult<UserRecord>> RegisterAsync(RegistrationRequest request)
        => PostAsync<UserRecord>("auth/register", request, ApiErrorContext.PublicAuth);

    public Task<ApiResult<AuthResponse>> LoginAsync(LoginRequest request)
        => PostAsync<AuthResponse>("auth/login", request, ApiErrorContext.Login);

    public Task<ApiResult<PasswordResetResponse>> RequestPasswordResetAsync(PasswordResetRequest request)
        => PostAsync<PasswordResetResponse>("auth/forgot-password", request, ApiErrorContext.PublicAuth);

    public Task<ApiResult<PasswordResetResponse>> ConfirmPasswordResetAsync(PasswordResetConfirmRequest request)
        => PostAsync<PasswordResetResponse>("auth/reset-password", request, ApiErrorContext.PublicAuth);

    public async Task<ApiResult> LogoutAsync()
    {
        try
        {
            if (string.IsNullOrWhiteSpace(_authState.AccessToken))
            {
                return ApiResult.Fail("No active access token was found.");
            }

            using var request = new HttpRequestMessage(HttpMethod.Post, "auth/logout");
            request.Headers.Authorization =
                new AuthenticationHeaderValue("Bearer", _authState.AccessToken);

            using var response = await _http.SendAsync(request);
            return await ApiResponseHelper.ReadEmptyResultAsync(response, _logger, ApiErrorContext.ProtectedResource);
        }
        catch (HttpRequestException ex)
        {
            _logger.LogWarning(ex, "Logout request failed");
            return ApiResult.Fail("Could not reach the server. Your local session will be cleared.");
        }
        catch (TaskCanceledException ex)
        {
            _logger.LogWarning(ex, "Logout request timed out");
            return ApiResult.Fail("The logout request timed out. Your local session will be cleared.");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Unexpected error during logout");
            return ApiResult.Fail("Could not complete server logout. Your local session will be cleared.");
        }
    }

    private async Task<ApiResult<T>> PostAsync<T>(string path, object payload, ApiErrorContext context)
    {
        try
        {
            using var response = await _http.PostAsJsonAsync(path, payload);
            return await ApiResponseHelper.ReadResultAsync<T>(response, path, _logger, context);
        }
        catch (HttpRequestException ex)
        {
            _logger.LogWarning(ex, "Network error calling {Path}", path);
            return ApiResult<T>.Fail("Cannot reach the server. Check your connection and try again.");
        }
        catch (TaskCanceledException ex)
        {
            _logger.LogWarning(ex, "Timeout calling {Path}", path);
            return ApiResult<T>.Fail("The request timed out. Please try again.");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Unexpected error calling {Path}", path);
            return ApiResult<T>.Fail("An unexpected error occurred. Please try again later.");
        }
    }
}