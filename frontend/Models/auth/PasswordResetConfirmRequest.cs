
using System.ComponentModel.DataAnnotations;

namespace Worksync.Frontend.Models.Auth;

public class PasswordResetConfirmRequest
{
    [Required]
    [EmailAddress]
    public string Email { get; set; } = string.Empty;

    [Required(ErrorMessage = "Verification code is required.")]
    [RegularExpression(@"^\d{6}$", ErrorMessage = "The code must be exactly 6 digits.")]
    public string ResetCode { get; set; } = string.Empty;

    [Required(ErrorMessage = "New password is required.")]
    [StringLength(128, MinimumLength = 12, ErrorMessage = "Password must be between 12 and 128 characters.")]
    public string NewPassword { get; set; } = string.Empty;
}