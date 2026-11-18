using System.Text.Json.Serialization;
using Worksync.Frontend.Models.Shared;

namespace Worksync.Frontend.Models.Users;

public class UserPageResponse
{
    public List<UserRecord> Content { get; set; } = new();
    public int Page { get; set; }
    public int Size { get; set; } = 20;
    public long TotalElements { get; set; }

    [JsonIgnore]
    public int TotalPages => Size <= 0
        ? 1
        : (int)Math.Max(1, Math.Ceiling((double)TotalElements / Size));
}