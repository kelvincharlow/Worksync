using Microsoft.AspNetCore.Components.Authorization;
using Worksync.Frontend.Services;
using Microsoft.AspNetCore.Components.Web;
using Microsoft.AspNetCore.Components.WebAssembly.Hosting;
using MudBlazor;
using MudBlazor.Services;
using Worksync.Frontend;
using Worksync.Frontend.Services.Projects;


var builder = WebAssemblyHostBuilder.CreateDefault(args);
builder.RootComponents.Add<App>("#app");
builder.RootComponents.Add<HeadOutlet>("head::after");

// Read and validate the API base URL from appsettings.json
var apiBaseUrl = builder.Configuration["ApiBaseUrl"]?.Trim()
    ?? throw new InvalidOperationException("ApiBaseUrl must be configured.");

if (!apiBaseUrl.EndsWith('/'))
    throw new InvalidOperationException("ApiBaseUrl must end with a trailing slash (e.g. http://localhost:8080/api/v1/).");

if (!Uri.TryCreate(apiBaseUrl, UriKind.Absolute, out var apiUri)
    || (apiUri.Scheme != Uri.UriSchemeHttp && apiUri.Scheme != Uri.UriSchemeHttps))
    throw new InvalidOperationException("ApiBaseUrl must be an absolute HTTP(S) URL.");
// Register HttpClient once using the configuration value
builder.Services.AddSingleton(new ApiConfiguration(apiUri));
// mudblazor services
builder.Services.AddMudServices(config =>
{
    config.SnackbarConfiguration.PositionClass = Defaults.Classes.Position.TopRight;
    config.SnackbarConfiguration.VisibleStateDuration = 4000;
    config.SnackbarConfiguration.ShowTransitionDuration = 200;
    config.SnackbarConfiguration.HideTransitionDuration = 200;
});


// Authentication & Authorization state
builder.Services.AddAuthorizationCore(options =>
{
    options.AddPolicy("ActiveUser", policy =>
        policy.RequireAuthenticatedUser()
              .RequireClaim("status", "ACTIVE"));

    options.AddPolicy("ActiveAdmin", policy =>
        policy.RequireAuthenticatedUser()
              .RequireClaim("status", "ACTIVE")
              .RequireRole("ADMIN"));

    // Only active users with explicit project creation permission.
    // Being an administrator does not automatically grant access.
    options.AddPolicy("CanCreateProject", policy =>
    policy.RequireAuthenticatedUser()
          .RequireClaim("status", "ACTIVE")
          .RequireClaim("permission", "PROJECT_CREATE")
          .RequireAssertion(context =>
              !context.User.IsInRole("ADMIN")));
              });

builder.Services.AddScoped<AuthState>();
// Project management services
builder.Services.AddScoped<ProjectContextState>();

builder.Services.AddScoped<InMemoryProjectWorkspaceService>();

builder.Services.AddScoped<IProjectService>(sp =>
    sp.GetRequiredService<InMemoryProjectWorkspaceService>());

builder.Services.AddScoped<IWorkItemService>(sp =>
    sp.GetRequiredService<InMemoryProjectWorkspaceService>());
builder.Services.AddScoped<AuthenticationStateProvider, WorksyncAuthenticationStateProvider>();
builder.Services.AddScoped<AuthSessionService>();
builder.Services.AddTransient<AuthMessageHandler>();

// Plain HttpClient for public auth & token refresh (prevents circular DI and refresh loops)
builder.Services.AddHttpClient<AuthApiService>(client =>
{
    client.BaseAddress = apiUri;
});

// Authenticated HttpClient for protected API calls (/users/me, /admin/users, etc.)
builder.Services.AddHttpClient("WorksyncApi", client =>
{
    client.BaseAddress = apiUri;
})
.AddHttpMessageHandler<AuthMessageHandler>();

// Default injected HttpClient uses the authenticated pipeline
builder.Services.AddScoped(sp =>
    sp.GetRequiredService<IHttpClientFactory>().CreateClient("WorksyncApi"));
builder.Services.AddScoped<UserApiService>();
await builder.Build().RunAsync();