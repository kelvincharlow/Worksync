
using Microsoft.AspNetCore.Components;

namespace Worksync.Frontend.Pages.Projects;

public partial class NewProject : ComponentBase
{
    protected List<WizardStep> Steps { get; } = new()
    {
        new(1, "Basics"),
        new(2, "Approach"),
        new(3, "Structure"),
        new(4, "Team"),
        new(5, "Workspace"),
        new(6, "Review")
    };

    protected int CurrentStep { get; set; } = 1;

    protected int TotalSteps => Steps.Count;

    // Project information
    protected string ProjectName { get; set; } = string.Empty;

    protected string ProjectGoal { get; set; } = string.Empty;

    protected DateOnly? StartDate { get; set; }

    protected DateOnly? TargetDate { get; set; }

    // Validation messages
    protected string? NameError { get; set; }

    protected string? GoalError { get; set; }

    protected string? DateError { get; set; }

    protected bool Validate()
    {
        NameError = string.IsNullOrWhiteSpace(ProjectName)
            ? "Project name is required."
            : ProjectName.Trim().Length > 100
                ? "Project name cannot exceed 100 characters."
                : null;

        GoalError = string.IsNullOrWhiteSpace(ProjectGoal)
            ? "Please describe the goal of this project."
            : ProjectGoal.Trim().Length > 1000
                ? "Project description cannot exceed 1000 characters."
                : null;

        DateError = StartDate.HasValue
                    && TargetDate.HasValue
                    && TargetDate.Value < StartDate.Value
            ? "Target date cannot be earlier than the start date."
            : null;

        return NameError is null
            && GoalError is null
            && DateError is null;
    }

    protected async Task HandleContinue()
    {
        if (!Validate())
            return;

        if (CurrentStep < TotalSteps)
        {
            CurrentStep++;
            return;
        }

        // Final project creation will be connected
        // to the project service in the Review step.
        await Task.CompletedTask;
    }

    protected void HandleBack()
    {
        if (CurrentStep > 1)
        {
            CurrentStep--;
        }
    }

    protected Task HandleCancel()
    {
        Navigation.NavigateTo("/home");

        return Task.CompletedTask;
    }

    protected Task HandleSaveDraft()
    {
        // Draft persistence will be connected
        // to the project draft service.
        return Task.CompletedTask;
    }

    [Inject]
    protected NavigationManager Navigation { get; set; } = default!;

    protected record WizardStep(int Number, string Label);
}
