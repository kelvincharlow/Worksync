using System.Net.Http.Json;
using Microsoft.Extensions.Logging;
using Worksync.Frontend.Models.Shared;
using Worksync.Frontend.Models.Users;

namespace Worksync.Frontend.Services;

public class UserApiService
{
    private readonly HttpClient _http;
    private readonly ILogger<UserApiService> _logger;

    public UserApiService(HttpClient http, ILogger<UserApiService> logger)
    {
        _http = http;
        _logger = logger;
    }

    // GET /api/v1/users/me
    public Task<ApiResult<UserRecord>> GetMyProfileAsync()
        => SendAsync<UserRecord>(HttpMethod.Get, "users/me");

    // PUT /api/v1/users/me
    public Task<ApiResult<UserRecord>> UpdateMyProfileAsync(UpdateMyProfileRequest request)
    {
        var sanitized = new UpdateMyProfileRequest
        {
            FirstName = request.FirstName.Trim(),
            LastName = request.LastName.Trim(),
            PhoneNumber = request.PhoneNumber.Trim()
        };

        return SendJsonAsync<UserRecord>(HttpMethod.Put, "users/me", sanitized);
    }

    // GET /api/v1/admin/users?status=...&page=...&size=...
    public Task<ApiResult<UserPageResponse>> GetAdminUsersAsync(
        string? status = null,
        int page = 0,
        int size = 20)
    {
        var safePage = Math.Max(0, page);
        var safeSize = Math.Clamp(size, 1, 100);

        var queryParts = new List<string>
        {
            $"page={safePage}",
            $"size={safeSize}"
        };

        if (!string.IsNullOrWhiteSpace(status)
            && !string.Equals(status, "ALL", StringComparison.OrdinalIgnoreCase))
        {
            queryParts.Add($"status={Uri.EscapeDataString(status.Trim())}");
        }

        var path = $"admin/users?{string.Join("&", queryParts)}";
        return SendAsync<UserPageResponse>(HttpMethod.Get, path);
    }

    // GET /api/v1/admin/users/{userId}
    public Task<ApiResult<UserRecord>> GetAdminUserByIdAsync(string userId)
        => SendAsync<UserRecord>(
            HttpMethod.Get,
            $"admin/users/{Uri.EscapeDataString(userId)}");

    // PATCH /api/v1/admin/users/{userId}/approve
    public Task<ApiResult<UserRecord>> ApproveUserAsync(string userId)
        => SendAsync<UserRecord>(
            HttpMethod.Patch,
            $"admin/users/{Uri.EscapeDataString(userId)}/approve");

    // PATCH /api/v1/admin/users/{userId}/reject
    public Task<ApiResult<UserRecord>> RejectUserAsync(string userId, string? reason = null)
    {
        var cleanReason = string.IsNullOrWhiteSpace(reason) ? null : reason.Trim();
        var body = new ReasonRequest { Reason = cleanReason };

        return SendJsonAsync<UserRecord>(
            HttpMethod.Patch,
            $"admin/users/{Uri.EscapeDataString(userId)}/reject",
            body);
    }

    // PATCH /api/v1/admin/users/{userId}/status
    public Task<ApiResult<UserRecord>> ChangeUserStatusAsync(
        string userId,
        string targetStatus,
        string? reason = null)
    {
        var body = new ChangeAccountStatusRequest
        {
            Status = targetStatus.Trim(),
            Reason = string.IsNullOrWhiteSpace(reason) ? null : reason.Trim()
        };

        return SendJsonAsync<UserRecord>(
            HttpMethod.Patch,
            $"admin/users/{Uri.EscapeDataString(userId)}/status",
            body);
    }

    private async Task<ApiResult<T>> SendAsync<T>(HttpMethod method, string path)
    {
        try
        {
            using var request = new HttpRequestMessage(method, path);
            using var response = await _http.SendAsync(request);
            return await ApiResponseHelper.ReadResultAsync<T>(
                response,
                path,
                _logger,
                ApiErrorContext.ProtectedResource);
        }
        catch (HttpRequestException ex)
        {
            _logger.LogWarning(ex, "Network error calling {Method} {Path}", method, path);
            return ApiResult<T>.Fail("Cannot reach the server. Check your connection and try again.");
        }
        catch (TaskCanceledException ex)
        {
            _logger.LogWarning(ex, "Timeout calling {Method} {Path}", method, path);
            return ApiResult<T>.Fail("The request timed out. Please try again.");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Unexpected error calling {Method} {Path}", method, path);
            return ApiResult<T>.Fail("An unexpected error occurred. Please try again.");
        }
    }

    private async Task<ApiResult<T>> SendJsonAsync<T>(HttpMethod method, string path, object payload)
    {
        try
        {
            using var request = new HttpRequestMessage(method, path)
            {
                Content = JsonContent.Create(payload)
            };
            using var response = await _http.SendAsync(request);
            return await ApiResponseHelper.ReadResultAsync<T>(
                response,
                path,
                _logger,
                ApiErrorContext.ProtectedResource);
        }
        catch (HttpRequestException ex)
        {
            _logger.LogWarning(ex, "Network error calling {Method} {Path}", method, path);
            return ApiResult<T>.Fail("Cannot reach the server. Check your connection and try again.");
        }
        catch (TaskCanceledException ex)
        {
            _logger.LogWarning(ex, "Timeout calling {Method} {Path}", method, path);
            return ApiResult<T>.Fail("The request timed out. Please try again.");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Unexpected error calling {Method} {Path}", method, path);
            return ApiResult<T>.Fail("An unexpected error occurred. Please try again.");
        }
    }
}