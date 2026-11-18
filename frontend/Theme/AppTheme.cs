using MudBlazor;

namespace Worksync.Frontend.Theme;

public static class AppTheme
{
    public static MudTheme Theme { get; } = new()
    {
        PaletteLight = new PaletteLight
        {
            Primary = "#6366F1",
            PrimaryDarken = "#4F46E5",
            PrimaryLighten = "#EEF2FF",
            Secondary = "#1B2A4A",
            Background = "#F6F8FC",
            Surface = "#FFFFFF",
            AppbarBackground = "#FFFFFF",
            AppbarText = "#0F172A",
            DrawerBackground = "#1B2A4A",
            DrawerText = "#CBD5E1",
            DrawerIcon = "#94A3B8",
            TextPrimary = "#0F172A",
            TextSecondary = "#64748B",
            Divider = "#E2E8F0",
            Success = "#16A34A",
            Warning = "#D97706",
            Error = "#DC2626"
        },
        LayoutProperties = new LayoutProperties
        {
            DefaultBorderRadius = "10px",
            DrawerWidthLeft = "256px"
        }
    };
}