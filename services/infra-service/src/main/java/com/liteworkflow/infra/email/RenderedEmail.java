package com.liteworkflow.infra.email;

public record RenderedEmail(String subject, String textBody, String htmlBody) {
}
