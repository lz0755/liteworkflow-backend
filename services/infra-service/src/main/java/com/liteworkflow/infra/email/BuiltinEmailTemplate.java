package com.liteworkflow.infra.email;

import com.liteworkflow.infra.notification.NotificationProperties;
import java.util.UUID;

public enum BuiltinEmailTemplate {
    WORKSPACE_MEMBER_ADDED("You were added to a workspace", "You now have access to a workspace."),
    PROJECT_MEMBER_ADDED("You were added to a project", "You now have access to a project."),
    ISSUE_ASSIGNED("An issue was assigned to you", "A project issue was assigned to you."),
    ISSUE_STATE_CHANGED("An issue changed state", "An issue you follow moved to a different state."),
    COMMENT_MENTION("You were mentioned in a comment", "A project member mentioned you in an issue comment.");

    private final String subject;
    private final String summary;

    BuiltinEmailTemplate(String subject, String summary) {
        this.subject = subject;
        this.summary = summary;
    }

    public RenderedEmail render(EmailOutboxJob job, NotificationProperties properties) {
        String link = targetLink(job, properties.getWebBaseUrl());
        String text = summary + "\n\nOpen liteworkflow: " + link;
        String html = "<p>" + summary + "</p><p><a href=\"" + link + "\">Open liteworkflow</a></p>";
        return new RenderedEmail(subject, text, html);
    }

    private String targetLink(EmailOutboxJob job, String baseUrl) {
        UUID resourceId = job.getResourceId();
        return switch (job.getResourceType()) {
            case "WORKSPACE" -> baseUrl + "/workspaces/" + resourceId;
            case "PROJECT" -> baseUrl + "/projects/" + resourceId;
            case "ISSUE" -> baseUrl + "/issues/" + resourceId;
            default -> job.getProjectId() == null
                    ? baseUrl
                    : baseUrl + "/projects/" + job.getProjectId();
        };
    }
}
