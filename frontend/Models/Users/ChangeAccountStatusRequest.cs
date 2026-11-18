using System.ComponentModel.DataAnnotations;
using System.Text.Json.Serialization;

namespace Worksync.Frontend.Models.Users;

public class ChangeAccountStatusRequest
{
    [Required(ErrorMessage = "Target status is required.")]
    [RegularExpression(@"^(ACTIVE|BLOCKED)$", ErrorMessage = "Status must be ACTIVE or BLOCKED.")]
    public string Status { get; set; } = string.Empty;

    [StringLength(500, ErrorMessage = "Reason must not exceed 500 characters.")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Reason { get; set; }
}