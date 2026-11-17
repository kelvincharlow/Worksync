using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using Microsoft.Extensions.Logging;
using Worksync.Frontend.Models.Auth;

namespace Worksync.Frontend.Services;

public enum ApiErrorContext
{
    Login,
    PublicAuth,
    ProtectedResource
}

public static class ApiResponseHelper
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };

    public static async Task<ApiResult<T>> ReadResultAsync<T>(
        HttpResponseMessage response,
        string path,
        ILogger logger,
        ApiErrorContext context = ApiErrorContext.ProtectedResource)
    {
        var statusCode = (int)response.StatusCode;

        if (response.IsSuccessStatusCode)
        {
            if (response.StatusCode == HttpStatusCode.NoContent)
            {
                return ApiResult<T>.Ok(default!, statusCode);
            }

            try
            {
                var data = await response.Content.ReadFromJsonAsync<T>(JsonOptions);
                if (data is null)
                {
                    return ApiResult<T>.Fail("The server returned an empty response.", statusCode);
                }

                return ApiResult<T>.Ok(data, statusCode);
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Malformed JSON response from {Path}", path);
                return ApiResult<T>.Fail("The server returned an unexpected response.", statusCode);
            }
        }

        var (message, code, fieldErrors) = await ExtractErrorAsync(response, context);
        return ApiResult<T>.Fail(message, statusCode, code, fieldErrors);
    }

    public static async Task<ApiResult> ReadEmptyResultAsync(
        HttpResponseMessage response,
        ILogger logger,
        ApiErrorContext context = ApiErrorContext.ProtectedResource)
    {
        var statusCode = (int)response.StatusCode;

        if (response.IsSuccessStatusCode)
        {
            return ApiResult.Ok(statusCode);
        }

        var (message, code, fieldErrors) = await ExtractErrorAsync(response, context);
        return ApiResult.Fail(message, statusCode, code, fieldErrors);
    }

    private static async Task<(string Message, string? Code, IReadOnlyList<FieldError>? FieldErrors)> ExtractErrorAsync(
        HttpResponseMessage response,
        ApiErrorContext context)
    {
        string? rawBody = null;
        try
        {
            rawBody = await response.Content.ReadAsStringAsync();
        }
        catch
        {
            // Ignore read failure and fall back to status-based message
        }

        ApiError? apiError = null;
        string? springDetail = null;

        if (!string.IsNullOrWhiteSpace(rawBody))
        {
            try
            {
                apiError = JsonSerializer.Deserialize<ApiError>(rawBody, JsonOptions);

                // Fallback for Spring ProblemDetail or default Spring error attributes (detail / error)
                if (string.IsNullOrWhiteSpace(apiError?.Message))
                {
                    using var doc = JsonDocument.Parse(rawBody);
                    if (doc.RootElement.ValueKind == JsonValueKind.Object)
                    {
                        if (doc.RootElement.TryGetProperty("detail", out var detailProp) && detailProp.ValueKind == JsonValueKind.String)
                            springDetail = detailProp.GetString();
                        else if (doc.RootElement.TryGetProperty("error", out var errProp) && errProp.ValueKind == JsonValueKind.String)
                            springDetail = errProp.GetString();
                    }
                }
            }
            catch
            {
                // Non-JSON response; do not expose raw HTML/text to the user
            }
        }

        var validFieldErrors = apiError?.FieldErrors?
            .Where(fe => !string.IsNullOrWhiteSpace(fe.Field) && !string.IsNullOrWhiteSpace(fe.Message))
            .ToList();

        var message = !string.IsNullOrWhiteSpace(apiError?.Message)
            ? apiError!.Message.Trim()
            : !string.IsNullOrWhiteSpace(springDetail)
                ? springDetail.Trim()
                : DefaultMessageFor(response.StatusCode, context);

        // If backend returned a generic message alongside field errors, ensure user gets useful context
        if (string.IsNullOrWhiteSpace(message))
        {
            message = DefaultMessageFor(response.StatusCode, context);
        }

        var code = string.IsNullOrWhiteSpace(apiError?.Code) ? null : apiError!.Code.Trim();
        return (message, code, validFieldErrors is { Count: > 0 } ? validFieldErrors : null);
    }

    public static string DefaultMessageFor(HttpStatusCode status, ApiErrorContext context) => (status, context) switch
    {
        (HttpStatusCode.BadRequest, _) => "Please check the highlighted fields and try again.",
        (HttpStatusCode.Unauthorized, ApiErrorContext.Login) => "Incorrect email or password.",
        (HttpStatusCode.Unauthorized, _) => "Your session has expired or is invalid. Please sign in again.",
        (HttpStatusCode.Forbidden, ApiErrorContext.Login) => "Your account cannot sign in. Contact your administrator.",
        (HttpStatusCode.Forbidden, _) => "You do not have permission to perform this action.",
        (HttpStatusCode.NotFound, ApiErrorContext.PublicAuth) => "The requested reset or account record could not be found.",
        (HttpStatusCode.NotFound, _) => "The requested record was not found.",
        (HttpStatusCode.Conflict, ApiErrorContext.PublicAuth) => "That email address is already registered.",
        (HttpStatusCode.Conflict, _) => "This action conflicts with the current state of the record.",
        (HttpStatusCode.TooManyRequests, _) => "Too many attempts. Please wait before trying again.",
        (HttpStatusCode.InternalServerError, _) => "The server ran into a problem. Please try again shortly.",
        _ => "The request could not be completed. Please try again."
    };
}