using Worksync.Frontend.Services;
using Microsoft.AspNetCore.Components.Web;
using Microsoft.AspNetCore.Components.WebAssembly.Hosting;
using MudBlazor.Services;
using Worksync.Frontend;

var builder = WebAssemblyHostBuilder.CreateDefault(args);
builder.RootComponents.Add<App>("#app");
builder.RootComponents.Add<HeadOutlet>("head::after");

// Read and validate the API base URL from appsettings.json
var apiBaseUrl = builder.Configuration["ApiBaseUrl"] 
    ?? throw new InvalidOperationException("ApiBaseUrl must be configured.");

if (!Uri.TryCreate(apiBaseUrl, UriKind.Absolute, out var apiUri)
    || (apiUri.Scheme != Uri.UriSchemeHttp && apiUri.Scheme != Uri.UriSchemeHttps))
    throw new InvalidOperationException("ApiBaseUrl must be an absolute HTTP(S) URL.");

// Register HttpClient once using the configuration value
builder.Services.AddScoped(_ => new HttpClient { BaseAddress = apiUri });

builder.Services.AddMudServices();

builder.Services.AddScoped<AuthState>();

Console.WriteLine(
    $"AuthState registered: {builder.Services.Any(x => x.ServiceType == typeof(AuthState))}"
);

await builder.Build().RunAsync();