using System.Reflection;
using Microsoft.AspNetCore.Components.Forms;
using Worksync.Frontend.Models.Auth;

namespace Worksync.Frontend.Services;

public static class EditContextExtensions
{
    /// <summary>
    /// Maps backend FieldErrors onto matching model properties in EditContext.
    /// Returns any errors that could not be matched to a property on the model.
    /// </summary>
    public static List<string> ApplyFieldErrors<TModel>(
        this EditContext editContext,
        ValidationMessageStore messageStore,
        TModel model,
        IReadOnlyList<FieldError>? fieldErrors)
        where TModel : class
    {
        var unmapped = new List<string>();
        if (fieldErrors is null || fieldErrors.Count == 0)
            return unmapped;

        var properties = typeof(TModel)
            .GetProperties(BindingFlags.Public | BindingFlags.Instance)
            .ToDictionary(p => p.Name, p => p.Name, StringComparer.OrdinalIgnoreCase);

        foreach (var error in fieldErrors)
        {
            if (string.IsNullOrWhiteSpace(error.Message))
                continue;

            var normalizedField = error.Field?.Trim() ?? string.Empty;
            if (properties.TryGetValue(normalizedField, out var propertyName))
            {
                messageStore.Add(new FieldIdentifier(model, propertyName), error.Message.Trim());
            }
            else
            {
                unmapped.Add(error.Message.Trim());
            }
        }

        editContext.NotifyValidationStateChanged();
        return unmapped;
    }
}