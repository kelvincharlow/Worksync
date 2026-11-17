using Microsoft.Extensions.Logging;
using Worksync.Frontend.Models.Shared;

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

    public async Task<ApiResult<UserRecord>> GetMyProfileAsync()
    {
        const string path = "users/me";
        try
        {
            using var response = await _http.GetAsync(path);
            return await ApiResponseHelper.ReadResultAsync<UserRecord>(
                response,
                path,
                _logger,
                ApiErrorContext.ProtectedResource);
        }
        catch (HttpRequestException ex)
        {
            _logger.LogWarning(ex, "Network error calling {Path}", path);
            return ApiResult<UserRecord>.Fail("Cannot reach the server. Check your connection and try again.");
        }
        catch (TaskCanceledException ex)
        {
            _logger.LogWarning(ex, "Timeout calling {Path}", path);
            return ApiResult<UserRecord>.Fail("The request timed out. Please try again.");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Unexpected error calling {Path}", path);
            return ApiResult<UserRecord>.Fail("An unexpected error occurred while loading your account details.");
        }
    }
}