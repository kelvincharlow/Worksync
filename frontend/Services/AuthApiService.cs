using System.Net;
using System.Net.Http.Json;
using Microsoft.Extensions.Logging;
using Worksync.Frontend.Models.Auth;
using Worksync.Frontend.Models.Shared;
using System.Net.Http.Headers;

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

    // ─────────────────────────────────────────────────────────────
    // Public endpoints
    // ─────────────────────────────────────────────────────────────

    public Task<ApiResult<UserRecord>> RegisterAsync(RegistrationRequest request)
        => PostAsync<UserRecord>("auth/register", request);

    public Task<ApiResult<AuthResponse>> LoginAsync(LoginRequest request)
        => PostAsync<AuthResponse>("auth/login", request);

    public Task<ApiResult<PasswordResetResponse>> RequestPasswordResetAsync(PasswordResetRequest request)
        => PostAsync<PasswordResetResponse>("auth/forgot-password", request);

    public Task<ApiResult<PasswordResetResponse>> ConfirmPasswordResetAsync(PasswordResetConfirmRequest request)
        => PostAsync<PasswordResetResponse>("auth/reset-password", request);

    // ─────────────────────────────────────────────────────────────
    // Authenticated endpoint (server-side revocation is "later"
    // per the spec; frontend still discards its in-memory token on
    // success/failure — that part is done by the caller).
    // ─────────────────────────────────────────────────────────────

    public async Task<ApiResult> LogoutAsync()
{
    try
    {
        if (string.IsNullOrWhiteSpace(_authState.AccessToken))
        {
            return ApiResult.Fail("No active access token was found.");
        }

        using var request = new HttpRequestMessage(
            HttpMethod.Post,
            "auth/logout");

        request.Headers.Authorization =
            new AuthenticationHeaderValue(
                "Bearer",
                _authState.AccessToken);

        using var response = await _http.SendAsync(request);

        if (response.IsSuccessStatusCode)
        {
            return ApiResult.Ok((int)response.StatusCode);
        }

        var apiError = await TryReadApiErrorAsync(response);

        return ApiResult.Fail(
            apiError?.Message ?? "Logout failed. Please try again.",
            (int)response.StatusCode);
    }
    catch (Exception ex)
    {
        _logger.LogWarning(ex, "Logout request failed");

        return ApiResult.Fail(
            "Could not reach the server. Your local session will be cleared.");
    }
}

    // ─────────────────────────────────────────────────────────────
    // Shared POST plumbing
    // ─────────────────────────────────────────────────────────────

    private async Task<ApiResult<T>> PostAsync<T>(string path, object payload)
    {
        try
        {
            using var response = await _http.PostAsJsonAsync(path, payload);
                return await ToResultAsync<T>(response, path);
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

    private async Task<ApiResult<T>> ToResultAsync<T>(
    HttpResponseMessage response,
    string path)
{
    if (response.IsSuccessStatusCode)
    {
        // Handle successful responses with no content.
        if (response.StatusCode == HttpStatusCode.NoContent)
        {
            return ApiResult<T>.Ok(default!, (int)response.StatusCode);
        }

        T? data;

        try
        {
            data = await response.Content.ReadFromJsonAsync<T>();
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Malformed JSON response from {Path}", path);

            return ApiResult<T>.Fail(
                "The server returned an unexpected response.",
                (int)response.StatusCode);
        }

        if (data is null)
        {
            return ApiResult<T>.Fail(
                "The server returned an empty response.",
                (int)response.StatusCode);
        }

        return ApiResult<T>.Ok(data, (int)response.StatusCode);
    }

    var apiError = await TryReadApiErrorAsync(response);

    var message = apiError?.Message ??
                  DefaultMessageFor(response.StatusCode);

    return ApiResult<T>.Fail(message, (int)response.StatusCode);
}
    private static async Task<ApiError?> TryReadApiErrorAsync(HttpResponseMessage response)
    {
        try
        {
            return await response.Content.ReadFromJsonAsync<ApiError>();
        }
        catch
        {
            return null;
        }
    }

    private static string DefaultMessageFor(HttpStatusCode status) => status switch
    {
        HttpStatusCode.BadRequest          => "Please check your input and try again.",
        HttpStatusCode.Unauthorized        => "Incorrect email or password.",
        HttpStatusCode.Forbidden           => "Your account cannot sign in. Contact your administrator.",
        HttpStatusCode.NotFound            => "We couldn't complete the request. Please start over.",
        HttpStatusCode.Conflict            => "That email is already registered.",
        HttpStatusCode.TooManyRequests     => "Too many attempts. Please wait a moment and try again.",
        HttpStatusCode.InternalServerError => "The server ran into a problem. Please try again.",
        _                                  => "Request failed. Please try again."
    };
}