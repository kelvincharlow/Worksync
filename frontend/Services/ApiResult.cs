namespace Worksync.Frontend.Services;

/// <summary>Result of an API call that returns no body (e.g. 204).</summary>
public record ApiResult(bool IsSuccess, string? ErrorMessage, int? StatusCode)
{
    public static ApiResult Ok(int? statusCode = 200)
        => new(true, null, statusCode);

    public static ApiResult Fail(string message, int? statusCode = null)
        => new(false, message, statusCode);
}

/// <summary>Result of an API call that returns a JSON body.</summary>
public record ApiResult<T>(bool IsSuccess, T? Data, string? ErrorMessage, int? StatusCode)
{
    public static ApiResult<T> Ok(T data, int? statusCode = 200)
        => new(true, data, null, statusCode);

    public static ApiResult<T> Fail(string message, int? statusCode = null)
        => new(false, default, message, statusCode);
}