using System.ComponentModel.DataAnnotations;

namespace Worksync.Frontend.Models.Auth;

public class PasswordResetConfirmRequest
{
    
    [Required(ErrorMessage = "Email is required.")]
    [EmailAddress(ErrorMessage = "Enter a valid email address.")]
    public string Email { get; set; } = string.Empty;

    [Required(ErrorMessage = "Reset code is required.")]
    [RegularExpression(@"^\d{6}$", ErrorMessage = "Enter a valid 6-digit reset code.")]
    public string ResetCode { get; set; } = string.Empty;

    [Required(ErrorMessage = "New password is required.")]
    [StringLength(128, MinimumLength = 12, ErrorMessage = "Password must be between 12 and 128 characters.")]
    public string NewPassword { get; set; } = string.Empty;
}