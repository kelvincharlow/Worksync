namespace Worksync.Frontend.Models.Projects;

public enum ProjectRole
{
    MANAGER,
    MEMBER,
    VIEWER
}

public enum ProjectResponsibility
{
    TECHNICAL_LEAD,
    DEVELOPER,
    QA_TESTER
}

public enum ProjectStatus
{
    ACTIVE,
    ARCHIVED
}

public enum WorkApproach
{
    Simple,
    Kanban
}

public enum ProjectAction
{
    VIEW_INTERNAL_WORK,
    VIEW_DELIVERY,
    CREATE_EPIC,
    CREATE_STORY,
    CREATE_TASK,
    UPDATE_ASSIGNED_WORK,
    ASSIGN_WORK,
    REPORT_BUG,
    TRIAGE_BUG,
    DESIGNATE_BUG_VERIFIER
}

public enum EffectiveWorkspaceRole
{
    None,
    Owner,
    Manager,
    TechnicalLead,
    Developer,
    QaTester,
    Viewer
}