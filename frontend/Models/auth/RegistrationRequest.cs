using System.ComponentModel.DataAnnotations;
namespace Worksync.Frontend.Models.Auth;

public class RegistrationRequest
{
    [Required(ErrorMessage = "First name is required.")]
    [StringLength(80, MinimumLength = 1, ErrorMessage = "First name must be between 1 and 80 characters.")]
    public string FirstName { get; set; } = string.Empty;

    [Required(ErrorMessage = "Last name is required.")]
    [StringLength(80, MinimumLength = 1, ErrorMessage = "Last name must be between 1 and 80 characters.")]
    public string LastName { get; set; } = string.Empty;

    [Required(ErrorMessage = "Email is required.")]
    [EmailAddress(ErrorMessage = "Invalid email format.")]
    [StringLength(254)]
    public string Email { get; set; } = string.Empty;

    [Required(ErrorMessage = "Phone number is required.")]
    [RegularExpression(@"^\+[1-9]\d{1,14}$", ErrorMessage = "Must use international E.164 format, e.g. +254712345678.")]
    public string PhoneNumber { get; set; } = string.Empty;

    [Required(ErrorMessage = "Password is required.")]
    [StringLength(128, MinimumLength = 12, ErrorMessage = "Password must be at least 12 characters long.")]
    public string Password { get; set; } = string.Empty;
}