
using Worksync.Frontend.Models.Shared;

namespace Worksync.Frontend.Models.Auth;

public class AuthResponse
{
    public string AccessToken { get; set; } = string.Empty;
    public string TokenType { get; set; } = "Bearer";
    public int ExpiresIn { get; set; }
    public UserRecord User { get; set; } = new();
}
