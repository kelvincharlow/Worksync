using System.Net;
using System.Net.Http.Headers;
using Microsoft.AspNetCore.Components;

namespace Worksync.Frontend.Services;

public class AuthMessageHandler : DelegatingHandler
{
    private readonly AuthState _authState;
    private readonly AuthSessionService _sessionService;
    private readonly NavigationManager _navigation;
    private readonly Uri _apiBaseUri;

    public AuthMessageHandler(
        AuthState authState,
        AuthSessionService sessionService,
        NavigationManager navigation,
        ApiConfiguration apiConfig)
    {
        _authState = authState;
        _sessionService = sessionService;
        _navigation = navigation;
        _apiBaseUri = apiConfig.BaseUri;
    }

    protected override async Task<HttpResponseMessage> SendAsync(
        HttpRequestMessage request,
        CancellationToken cancellationToken)
    {
        // Never attach tokens to unrelated external URLs
        if (!IsWorksyncApiRequest(request.RequestUri))
        {
            return await base.SendAsync(request, cancellationToken);
        }
        
        await _authState.InitializeAsync();

        if (IsOnPublicAuthRoute())
        {
            return await base.SendAsync(request, cancellationToken);
        }

        // Proactively refresh if access token is expired/expiring and we have a refresh token
        if (!_authState.IsAccessTokenValid && _authState.HasRefreshToken)
        {
            var refreshed = await _sessionService.TryRefreshAsync();
            if (!refreshed)
            {
                RedirectToExpiredLogin();
                return new HttpResponseMessage(HttpStatusCode.Unauthorized)
                {
                    RequestMessage = request
                };
            }
        }

        var tokenUsed = _authState.AccessToken;
        if (!string.IsNullOrWhiteSpace(tokenUsed))
        {
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", tokenUsed);
        }

        // Buffer content only if a 401 retry might be needed
        byte[]? contentBytes = null;
        List<KeyValuePair<string, IEnumerable<string>>>? contentHeaders = null;
        if (request.Content is not null && _authState.HasRefreshToken)
        {
            contentBytes = await request.Content.ReadAsByteArrayAsync(cancellationToken);
            contentHeaders = request.Content.Headers
                .Select(h => new KeyValuePair<string, IEnumerable<string>>(h.Key, h.Value.ToArray()))
                .ToList();
        }

        var response = await base.SendAsync(request, cancellationToken);

        if (response.StatusCode != HttpStatusCode.Unauthorized)
        {
            return response;
        }

        // Handle 401: try refresh-token rotation once if available
        if (_authState.HasRefreshToken)
        {
            var refreshed = await _sessionService.TryRefreshAsync(tokenUsed);
            if (refreshed && !string.IsNullOrWhiteSpace(_authState.AccessToken))
            {
                response.Dispose();

                using var retryRequest = CloneRequest(request, contentBytes, contentHeaders);
                retryRequest.Headers.Authorization =
                    new AuthenticationHeaderValue("Bearer", _authState.AccessToken);

                return await base.SendAsync(retryRequest, cancellationToken);
            }
        }

        // Refresh failed or no refresh token: clear local session
        await _authState.ClearAsync();
        RedirectToExpiredLogin();

        return response;
    }

    private bool IsWorksyncApiRequest(Uri? requestUri)
    {
        if (requestUri is null) return false;
        if (!requestUri.IsAbsoluteUri) return true;

        return Uri.Compare(
                   requestUri,
                   _apiBaseUri,
                   UriComponents.SchemeAndServer,
                   UriFormat.Unescaped,
                   StringComparison.OrdinalIgnoreCase) == 0
               && requestUri.AbsolutePath.StartsWith(
                   _apiBaseUri.AbsolutePath,
                   StringComparison.OrdinalIgnoreCase);
    }
    private bool IsOnPublicAuthRoute()
{
    var relativePath = _navigation.ToBaseRelativePath(_navigation.Uri)
        .Split('?', '#')[0]
        .Trim('/')
        .ToLowerInvariant();

    return relativePath is "" or "login" or "register" or "forgot-password" or "reset-password";
}

    private void RedirectToExpiredLogin()
    {
        var relativePath = _navigation.ToBaseRelativePath(_navigation.Uri)
            .Split('?', '#')[0]
            .Trim('/')
            .ToLowerInvariant();

        if (relativePath is not ("login" or "register" or "forgot-password" or "reset-password"))
        {
            _navigation.NavigateTo("/login?sessionExpired=true");
        }
    }

    private static HttpRequestMessage CloneRequest(
        HttpRequestMessage original,
        byte[]? contentBytes,
        List<KeyValuePair<string, IEnumerable<string>>>? contentHeaders)
    {
        var clone = new HttpRequestMessage(original.Method, original.RequestUri);

        foreach (var header in original.Headers)
        {
            if (!string.Equals(header.Key, "Authorization", StringComparison.OrdinalIgnoreCase))
            {
                clone.Headers.TryAddWithoutValidation(header.Key, header.Value);
            }
        }

        if (contentBytes is not null)
        {
            var newContent = new ByteArrayContent(contentBytes);
            if (contentHeaders is not null)
            {
                foreach (var header in contentHeaders)
                {
                    newContent.Headers.TryAddWithoutValidation(header.Key, header.Value);
                }
            }
            clone.Content = newContent;
        }

        return clone;
    }
}

public sealed record ApiConfiguration(Uri BaseUri);