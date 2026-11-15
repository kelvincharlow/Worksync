using System.ComponentModel.DataAnnotations;

namespace Worksync.Frontend.Models.Auth;

public class PasswordResetConfirmRequest
{
    [Required]
    [EmailAddress]
    public string Email { get; set; } = string.Empty;

    [Required]
    [RegularExpression(@"^\d{6}$")]
    public string ResetCode { get; set; } = string.Empty;

    [Required]
    [StringLength(128, MinimumLength = 12)]
    public string NewPassword { get; set; } = string.Empty;
}