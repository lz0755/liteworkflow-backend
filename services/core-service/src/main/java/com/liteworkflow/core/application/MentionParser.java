package com.liteworkflow.core.application;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Extracts only canonical, server-verifiable user identifiers; display labels are never identities. */
@Component
public class MentionParser {

    private static final String UUID_PATTERN =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final Pattern ANGLE_TOKEN = Pattern.compile("<@(" + UUID_PATTERN + ")>");
    private static final Pattern MARKDOWN_TOKEN = Pattern.compile(
            "@\\[[^]\\r\\n]{1,120}]\\((?:user:)?(" + UUID_PATTERN + ")\\)");

    public Set<UUID> parse(String body) {
        LinkedHashSet<UUID> userIds = new LinkedHashSet<>();
        addMatches(ANGLE_TOKEN.matcher(body), userIds);
        addMatches(MARKDOWN_TOKEN.matcher(body), userIds);
        return Set.copyOf(userIds);
    }

    private void addMatches(Matcher matcher, Set<UUID> userIds) {
        while (matcher.find()) {
            userIds.add(UUID.fromString(matcher.group(1)));
        }
    }
}
