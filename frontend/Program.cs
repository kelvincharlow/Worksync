using Worksync.Frontend.Services;
using Microsoft.AspNetCore.Components.Web;
using Microsoft.AspNetCore.Components.WebAssembly.Hosting;
using MudBlazor;
using MudBlazor.Services;
using Worksync.Frontend;


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
builder.Services.AddScoped(_ => new HttpClient { BaseAddress = apiUri });

builder.Services.AddMudServices(config =>
{
    config.SnackbarConfiguration.PositionClass = Defaults.Classes.Position.TopRight;
    config.SnackbarConfiguration.VisibleStateDuration = 4000;
    config.SnackbarConfiguration.ShowTransitionDuration = 200;
    config.SnackbarConfiguration.HideTransitionDuration = 200;
});

builder.Services.AddScoped<AuthState>();

builder.Services.AddScoped<AuthApiService>();

await builder.Build().RunAsync();