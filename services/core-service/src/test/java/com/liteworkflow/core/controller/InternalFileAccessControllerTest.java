package com.liteworkflow.core.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.core.application.PermissionService;
import com.liteworkflow.core.config.CoreProperties;
import com.liteworkflow.core.repository.IssueRepository;
import com.liteworkflow.core.repository.ProjectRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InternalFileAccessControllerTest {
    private final PermissionService permissions = mock(PermissionService.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final IssueRepository issues = mock(IssueRepository.class);
    private InternalFileAccessController controller;

    @BeforeEach
    void setUp() {
        CoreProperties properties = new CoreProperties();
        properties.setInternalToken("internal-test-token");
        controller = new InternalFileAccessController(permissions, projects, issues, properties);
    }

    @Test
    void authorizesWorkspaceReadsAndWritesWithDifferentPermissionLevels() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        assertThat(controller.authorize("internal-test-token", "WORKSPACE", workspaceId, userId, "READ")
                .data().workspaceId()).isEqualTo(workspaceId);
        controller.authorize("internal-test-token", "WORKSPACE", workspaceId, userId, "WRITE");

        verify(permissions).requireWorkspaceMember(workspaceId, userId);
        verify(permissions).requireWorkspaceMemberManager(workspaceId, userId);
    }

    @Test
    void rejectsInvalidInternalCredentialBeforePermissionLookup() {
        assertThatThrownBy(() -> controller.authorize("wrong-token", "WORKSPACE",
                UUID.randomUUID(), UUID.randomUUID(), "READ"))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }
}
