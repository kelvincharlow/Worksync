using System.Security.Claims;
using Microsoft.AspNetCore.Components.Authorization;

namespace Worksync.Frontend.Services;

public class WorksyncAuthenticationStateProvider
    : AuthenticationStateProvider, IDisposable
{
    private static readonly AuthenticationState Anonymous =
        new(new ClaimsPrincipal(new ClaimsIdentity()));

    private readonly AuthState _authState;

    public WorksyncAuthenticationStateProvider(AuthState authState)
    {
        _authState = authState;
        _authState.OnChange += HandleAuthStateChanged;
    }

    public override async Task<AuthenticationState>
        GetAuthenticationStateAsync()
    {
        await _authState.InitializeAsync();

        if (!_authState.IsAuthenticated || _authState.User is null)
        {
            return Anonymous;
        }

        var user = _authState.User;
        var fullName = $"{user.FirstName} {user.LastName}".Trim();

        var claims = new List<Claim>
        {
            new(ClaimTypes.NameIdentifier, user.Id),
            new(
                ClaimTypes.Name,
                string.IsNullOrWhiteSpace(fullName)
                    ? user.Email
                    : fullName),
            new(ClaimTypes.Email, user.Email),
            new(ClaimTypes.Role, user.Role),
            new("status", user.Status)
        };

        // Include explicitly assigned permissions.
        foreach (var permission in user.Permissions)
        {
            if (!string.IsNullOrWhiteSpace(permission))
            {
                claims.Add(
                    new Claim("permission", permission.Trim()));
            }
        }

        var identity = new ClaimsIdentity(
            claims,
            authenticationType: "WorksyncJwt");

        return new AuthenticationState(
            new ClaimsPrincipal(identity));
    }

    private void HandleAuthStateChanged()
    {
        NotifyAuthenticationStateChanged(
            GetAuthenticationStateAsync());
    }

    public void Dispose()
    {
        _authState.OnChange -= HandleAuthStateChanged;
    }
}