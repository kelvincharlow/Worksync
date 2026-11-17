
namespace Worksync.Frontend.Models.Shared;

public class UserRecord
{
    public string Id { get; set; } = "";
    public string FirstName { get; set; } = "";
    public string LastName { get; set; } = "";
    public string Email { get; set; } = "";
    public string PhoneNumber { get; set; } = "";
    public string Role { get; set; } = "USER";
    public string Status { get; set; } = "PENDING_APPROVAL";
    public DateTimeOffset? CreatedAt { get; set; }
    public DateTimeOffset? UpdatedAt { get; set; }
    public List<string> Permissions { get; set; } = new();

}
