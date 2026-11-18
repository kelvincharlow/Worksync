using System.ComponentModel.DataAnnotations;
using System.Text.Json.Serialization;

namespace Worksync.Frontend.Models.Users;

public class ReasonRequest
{
    [StringLength(500, ErrorMessage = "Reason must not exceed 500 characters.")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Reason { get; set; }
}