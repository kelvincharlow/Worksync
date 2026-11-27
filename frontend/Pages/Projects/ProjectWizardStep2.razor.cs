
using Microsoft.AspNetCore.Components;

namespace Worksync.Frontend.Pages.Projects;

public partial class ProjectWizardStep2 : ComponentBase
{
    // The six steps displayed in the progress tracker.
    protected List<WizardStep> Steps { get; } = new()
    {
        new WizardStep(1, "Basics"),
        new WizardStep(2, "Approach"),
        new WizardStep(3, "Structure"),
        new WizardStep(4, "Team"),
        new WizardStep(5, "Workspace"),
        new WizardStep(6, "Review")
    };

    [Parameter]
    public int CurrentStep { get; set; } = 2;

    [Parameter]
    public int TotalSteps { get; set; } = 6;

    // Available project approaches.
    protected List<ApproachOption> Approaches { get; } = new()
    {
        new ApproachOption(
            Id: "task-tracking",
            Title: "Simple task tracking",
            Description: "A clear, flexible list for teams across any department.",
            IconSvgPath: "icon-list",
            Disabled: false,
            Badge: null),

        new ApproachOption(
            Id: "visual-board",
            Title: "Visual board",
            Description: "Manage the same tasks across To Do, In Progress, Review, and Done.",
            IconSvgPath: "icon-board",
            Disabled: false,
            Badge: null),

        new ApproachOption(
            Id: "sprint-planning",
            Title: "Sprint planning",
            Description: "Backlog, sprint cycles, and estimation in a later module.",
            IconSvgPath: "icon-calendar",
            Disabled: true,
            Badge: "Coming later")
    };

    // Selected approach supplied by the parent wizard.
    [Parameter]
    public string? SelectedApproachId { get; set; } = "task-tracking";

    [Parameter]
    public EventCallback<string?> SelectedApproachIdChanged { get; set; }

    // Navigation and draft callbacks.
    [Parameter]
    public EventCallback OnBack { get; set; }

    [Parameter]
    public EventCallback OnContinue { get; set; }

    [Parameter]
    public EventCallback OnSaveDraft { get; set; }

    // Local selection state.
    private string? _selectedApproachId;

    protected override void OnParametersSet()
    {
        _selectedApproachId = SelectedApproachId;
    }

    protected bool IsSelected(ApproachOption option)
    {
        return option.Id == _selectedApproachId;
    }

    protected async Task SelectApproachAsync(ApproachOption option)
    {
        if (option.Disabled)
        {
            return;
        }

        _selectedApproachId = option.Id;

        await SelectedApproachIdChanged.InvokeAsync(
            _selectedApproachId);
    }

    protected async Task HandleContinue()
    {
        if (!string.IsNullOrWhiteSpace(_selectedApproachId))
        {
            await OnContinue.InvokeAsync();
        }
    }

    protected async Task HandleBack()
    {
        await OnBack.InvokeAsync();
    }

    protected async Task HandleSaveDraft()
    {
        await OnSaveDraft.InvokeAsync();
    }

    protected record WizardStep(
        int Number,
        string Label);

    protected record ApproachOption(
        string Id,
        string Title,
        string Description,
        string IconSvgPath,
        bool Disabled,
        string? Badge);
}
