
using MudBlazor;

namespace Worksync.Frontend.Theme;

public static class AppTheme
{
    // Core palette
    public const string Navy = "#1B2A4A";
    public const string Indigo = "#3F51B5";
    public const string White = "#FFFFFF";
    public const string LightGrey = "#F4F5F7";
    public const string MidGrey = "#E0E2E7";
    public const string TextDark = "#1A1A1A";

    // Status colors
    public const string StatusGreen = "#2E7D32";
    public const string StatusRed = "#C62828";
    public const string StatusYellow = "#F9A825";
    public const string StatusPurple = "#6A1B9A";

    public static MudTheme Theme => new()
    {
        PaletteLight = new PaletteLight
        {
            Primary = Navy,
            Secondary = Indigo,
            Background = LightGrey,
            Surface = White,
            AppbarBackground = Navy,
            DrawerBackground = White,
            DrawerText = TextDark,
            TextPrimary = TextDark,
            TextSecondary = "#5A5F6B",
            ActionDefault = Indigo,
            LinesDefault = MidGrey,
            TableLines = MidGrey,
            Divider = MidGrey
        }
    };
}
