using System.Net;
using System.Net.Http.Json;
using Microsoft.Extensions.Logging;
using Worksync.Frontend.Models.Auth;
using Worksync.Frontend.Models.Shared;

namespace Worksync.Frontend.Services;

public class AuthApiService
{
    private readonly HttpClient _http;
    private readonly ILogger<AuthApiService> _logger;

    public AuthApiService(HttpClient http, ILogger<AuthApiService> logger)
    {
        _http = http;
        _logger = logger;
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
            var response = await _http.PostAsync("auth/logout", content: null);

            if (response.IsSuccessStatusCode)
                return ApiResult.Ok((int)response.StatusCode);

            var apiError = await TryReadApiErrorAsync(response);
            return ApiResult.Fail(
                apiError?.Message ?? "Logout failed on the server.",
                (int)response.StatusCode);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Logout request failed");
            // Even if the server call fails, the caller will still clear local state.
            return ApiResult.Fail("Could not reach the server. Session cleared locally.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Shared POST plumbing
    // ─────────────────────────────────────────────────────────────

    private async Task<ApiResult<T>> PostAsync<T>(string path, object payload)
    {
        try
        {
            var response = await _http.PostAsJsonAsync(path, payload);
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

    private async Task<ApiResult<T>> ToResultAsync<T>(HttpResponseMessage response, string path)
    {
        if (response.IsSuccessStatusCode)
        {
            T? data;
            try
            {
                data = await response.Content.ReadFromJsonAsync<T>();
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Malformed JSON response from {Path}", path);
                return ApiResult<T>.Fail("The server returned an unexpected response.");
            }

            if (data is null)
                return ApiResult<T>.Fail("The server returned an empty response.");

            return ApiResult<T>.Ok(data, (int)response.StatusCode);
        }

        var apiError = await TryReadApiErrorAsync(response);
        var message = apiError?.Message ?? DefaultMessageFor(response.StatusCode);
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