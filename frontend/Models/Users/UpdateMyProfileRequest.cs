using System.ComponentModel.DataAnnotations;

namespace Worksync.Frontend.Models.Users;

public class UpdateMyProfileRequest
{
    [Required(ErrorMessage = "First name is required.")]
    [StringLength(80, MinimumLength = 1, ErrorMessage = "First name must be between 1 and 80 characters.")]
    [RegularExpression(@".*\S.*", ErrorMessage = "First name cannot be blank.")]
    public string FirstName { get; set; } = string.Empty;

    [Required(ErrorMessage = "Last name is required.")]
    [StringLength(80, MinimumLength = 1, ErrorMessage = "Last name must be between 1 and 80 characters.")]
    [RegularExpression(@".*\S.*", ErrorMessage = "Last name cannot be blank.")]
    public string LastName { get; set; } = string.Empty;

    [Required(ErrorMessage = "Phone number is required.")]
    [RegularExpression(@"^\+[1-9]\d{1,14}$", ErrorMessage = "Must use international E.164 format, e.g. +254712345678.")]
    public string PhoneNumber { get; set; } = string.Empty;
}