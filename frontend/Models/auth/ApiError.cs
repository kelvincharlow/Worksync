namespace Worksync.Frontend.Models.Auth;

public class ApiError
{
    public string Code { get; set; } = string.Empty;
    public string Message { get; set; } = string.Empty;
    public DateTimeOffset Timestamp { get; set; }
    public List<FieldError>? FieldErrors { get; set; }
}