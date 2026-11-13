
using System.ComponentModel.DataAnnotations;

namespace Worksync.Frontend.Models.Auth;

public class PasswordResetRequest
{
    [Required(ErrorMessage = "Email is required.")]
    [EmailAddress(ErrorMessage = "Enter a valid email address.")]
    [StringLength(254)]
    public string Email { get; set; } = string.Empty;
}