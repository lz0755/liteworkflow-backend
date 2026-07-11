package com.liteworkflow.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.common.core.event.EventScope;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.security.jwt.JwtTokenService;
import com.liteworkflow.common.security.user.CurrentUser;
import com.liteworkflow.core.directory.IdentityUserEventPayload;
import com.liteworkflow.core.directory.UserDirectoryEventConsumer;
import com.liteworkflow.core.directory.UserDirectoryProjectionService;
import com.liteworkflow.core.domain.AccountStatus;
import com.liteworkflow.core.domain.MemberStatus;
import com.liteworkflow.core.domain.WorkspaceRole;
import com.liteworkflow.core.dto.request.CreateWorkspaceRequest;
import com.liteworkflow.core.outbox.CoreEventPublisher;
import com.liteworkflow.core.repository.ActivityRepository;
import com.liteworkflow.core.repository.ConsumedEventRepository;
import com.liteworkflow.core.repository.LocalOutboxEventRepository;
import com.liteworkflow.core.repository.IssueStateRepository;
import com.liteworkflow.core.repository.ProjectMemberRepository;
import com.liteworkflow.core.repository.ProjectRepository;
import com.liteworkflow.core.repository.UserDirectoryRepository;
import com.liteworkflow.core.repository.UserProfileRepository;
import com.liteworkflow.core.repository.WorkspaceMemberRepository;
import com.liteworkflow.core.repository.WorkspaceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class CoreM4IntegrationTest {

    @Autowired private UserDirectoryProjectionService projectionService;
    @Autowired private UserDirectoryEventConsumer directoryEventConsumer;
    @Autowired private WorkspaceApplicationService workspaceService;
    @Autowired private WorkspaceMemberApplicationService memberService;
    @Autowired private UserDirectoryRepository userDirectoryRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private LocalOutboxEventRepository outboxRepository;
    @Autowired private IssueStateRepository issueStateRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ConsumedEventRepository consumedEventRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenService jwtTokenService;

    @MockBean private CoreEventPublisher coreEventPublisher;
    @MockBean private WorkspacePermissionCache permissionCache;

    @BeforeEach
    void resetState() {
        reset(coreEventPublisher, permissionCache);
        outboxRepository.deleteAll();
        activityRepository.deleteAll();
        issueStateRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        memberRepository.deleteAll();
        workspaceRepository.deleteAll();
        userProfileRepository.deleteAll();
        consumedEventRepository.deleteAll();
        userDirectoryRepository.deleteAll();
    }

    @Test
    void identityConsumerIgnoresDuplicateAndOutOfOrderSourceVersions() throws Exception {
        UUID userId = UUID.randomUUID();
        EventEnvelope<IdentityUserEventPayload> versionTwo = event(
                UUID.randomUUID(), "identity.user.updated", userId,
                "new@example.com", "New Name", AccountStatus.ACTIVE, 2);

        directoryEventConsumer.consume(versionTwo);
        assertThat(projectionService.consume(event(
                UUID.randomUUID(), "identity.user.registered", userId,
                "old@example.com", "Old Name", AccountStatus.ACTIVE, 1))).isFalse();
        assertThat(projectionService.consume(versionTwo)).isFalse();

        var projected = userDirectoryRepository.findById(userId).orElseThrow();
        assertThat(projected.getNormalizedEmail()).isEqualTo("new@example.com");
        assertThat(projected.getDisplayName()).isEqualTo("New Name");
        assertThat(projected.getSourceVersion()).isEqualTo(2);
        assertThat(consumedEventRepository.count()).isEqualTo(2);
        assertThat(userProfileRepository.existsById(userId)).isTrue();
    }

    @Test
    void onlyOwnerOrAdminCanSearchAndResponseDoesNotLeakInternalFieldsOrKeyword(CapturedOutput output)
            throws Exception {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID memberId = activeUser("member@example.com", "Member");
        String privateKeyword = "ultra-private-search@example.com";
        UUID candidateId = activeUser(privateKeyword, "Candidate Person");
        UUID workspaceId = workspaceService.create(
                ownerId, new CreateWorkspaceRequest("Search Workspace", null)).id();
        memberService.add(ownerId, workspaceId, memberId, WorkspaceRole.MEMBER);

        mockMvc.perform(get("/api/v1/users/search")
                        .headers(identityHeaders(ownerId, "owner@example.com"))
                        .param("keyword", privateKeyword)
                        .param("contextType", "WORKSPACE")
                        .param("contextId", workspaceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId").value(candidateId.toString()))
                .andExpect(jsonPath("$.data.records[0].email").value(privateKeyword))
                .andExpect(jsonPath("$.data.records[0].eligible").value(true))
                .andExpect(jsonPath("$.data.records[0].sourceVersion").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].accountStatus").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].createdAt").doesNotExist());

        mockMvc.perform(get("/api/v1/users/search")
                        .headers(identityHeaders(memberId, "member@example.com"))
                        .param("keyword", "candidate")
                        .param("contextType", "WORKSPACE")
                        .param("contextId", workspaceId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(CoreErrorCode.WORKSPACE_MEMBER_PERMISSION_DENIED.code()));

        assertThat(output.getAll()).doesNotContain(privateKeyword);
    }

    @Test
    void adminCanSearchByDisplayNameAndEmailCaseInsensitively() throws Exception {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID adminId = activeUser("admin@example.com", "Workspace Admin");
        UUID displayNameCandidateId = activeUser("first@example.com", "Alice Searchable");
        UUID emailCandidateId = activeUser("bob.candidate@example.com", "Second Candidate");
        UUID workspaceId = workspaceService.create(
                ownerId, new CreateWorkspaceRequest("Admin Search", null)).id();
        memberService.add(ownerId, workspaceId, adminId, WorkspaceRole.ADMIN);

        mockMvc.perform(get("/api/v1/users/search")
                        .headers(identityHeaders(adminId, "admin@example.com"))
                        .param("keyword", "ALICE SEARCH")
                        .param("contextType", "workspace")
                        .param("contextId", workspaceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId")
                        .value(displayNameCandidateId.toString()));

        mockMvc.perform(get("/api/v1/users/search")
                        .headers(identityHeaders(adminId, "admin@example.com"))
                        .param("keyword", "BOB.CANDIDATE@EXAMPLE")
                        .param("contextType", "WORKSPACE")
                        .param("contextId", workspaceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId")
                        .value(emailCandidateId.toString()));
    }

    @Test
    void searchRejectsEnumerationKeywordsAndOversizedPages() throws Exception {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID workspaceId = workspaceService.create(
                ownerId, new CreateWorkspaceRequest("Search Validation", null)).id();

        for (String keyword : List.of(" ", "a", "%%")) {
            mockMvc.perform(get("/api/v1/users/search")
                            .headers(identityHeaders(ownerId, "owner@example.com"))
                            .param("keyword", keyword)
                            .param("contextType", "WORKSPACE")
                            .param("contextId", workspaceId.toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(CoreErrorCode.USER_SEARCH_KEYWORD_TOO_SHORT.code()));
        }

        mockMvc.perform(get("/api/v1/users/search")
                        .headers(identityHeaders(ownerId, "owner@example.com"))
                        .param("keyword", "valid")
                        .param("contextType", "WORKSPACE")
                        .param("contextId", workspaceId.toString())
                        .param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disabledUsersAreNeitherCandidatesNorAddable() throws Exception {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID disabledId = user(
                "identity.user.disabled", "disabled@example.com", "Disabled Candidate", AccountStatus.DISABLED, 1);
        UUID workspaceId = workspaceService.create(
                ownerId, new CreateWorkspaceRequest("Disabled Test", null)).id();

        mockMvc.perform(get("/api/v1/users/search")
                        .headers(identityHeaders(ownerId, "owner@example.com"))
                        .param("keyword", "disabled")
                        .param("contextType", "WORKSPACE")
                        .param("contextId", workspaceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        assertThatThrownBy(() -> memberService.add(ownerId, workspaceId, disabledId, WorkspaceRole.MEMBER))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(CoreErrorCode.USER_NOT_ACTIVE);
    }

    @Test
    void excludeExistingMembersCanBeDisabledWithoutMakingExistingMemberEligible() throws Exception {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID existingId = activeUser("existing@example.com", "Existing Member");
        UUID workspaceId = workspaceService.create(
                ownerId, new CreateWorkspaceRequest("Existing Test", null)).id();
        memberService.add(ownerId, workspaceId, existingId, WorkspaceRole.MEMBER);

        mockMvc.perform(get("/api/v1/users/search")
                        .headers(identityHeaders(ownerId, "owner@example.com"))
                        .param("keyword", "existing")
                        .param("contextType", "WORKSPACE")
                        .param("contextId", workspaceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/v1/users/search")
                        .headers(identityHeaders(ownerId, "owner@example.com"))
                        .param("keyword", "existing")
                        .param("contextType", "WORKSPACE")
                        .param("contextId", workspaceId.toString())
                        .param("excludeExistingMembers", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].workspaceMember").value(true))
                .andExpect(jsonPath("$.data.records[0].eligible").value(false))
                .andExpect(jsonPath("$.data.records[0].ineligibleReason")
                        .value(CoreErrorCode.WORKSPACE_MEMBER_ALREADY_EXISTS.name()));
    }

    @Test
    void duplicateAddIsRejectedAndSuccessfulChangesShareActivityOutboxTransaction() {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID candidateId = activeUser("candidate@example.com", "Candidate");
        UUID workspaceId = workspaceService.create(
                ownerId, new CreateWorkspaceRequest("Audit Test", null)).id();

        memberService.add(ownerId, workspaceId, candidateId, WorkspaceRole.MEMBER);

        assertThat(activityRepository.count()).isEqualTo(2);
        assertThat(outboxRepository.count()).isEqualTo(2);
        verify(permissionCache).evict(workspaceId, candidateId);
        assertThatThrownBy(() -> memberService.add(
                        ownerId, workspaceId, candidateId, WorkspaceRole.VIEWER))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(CoreErrorCode.WORKSPACE_MEMBER_ALREADY_EXISTS);
        assertThat(activityRepository.count()).isEqualTo(2);
        assertThat(outboxRepository.count()).isEqualTo(2);
    }

    @Test
    void workspacePermissionsDoNotCrossWorkspaceBoundary() {
        UUID firstOwner = activeUser("first@example.com", "First");
        UUID secondOwner = activeUser("second@example.com", "Second");
        UUID firstWorkspace = workspaceService.create(
                firstOwner, new CreateWorkspaceRequest("First Workspace", null)).id();
        UUID secondWorkspace = workspaceService.create(
                secondOwner, new CreateWorkspaceRequest("Second Workspace", null)).id();

        assertThatThrownBy(() -> memberService.list(firstOwner, secondWorkspace, 1, 20))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(CoreErrorCode.WORKSPACE_PERMISSION_DENIED);
        assertThat(memberService.list(firstOwner, firstWorkspace, 1, 20).total()).isEqualTo(1);
    }

    @Test
    void lastOwnerCannotBeDemotedOrRemoved() {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID workspaceId = workspaceService.create(
                ownerId, new CreateWorkspaceRequest("Owner Test", null)).id();

        assertLastOwnerFailure(() -> memberService.changeRole(
                ownerId, workspaceId, ownerId, WorkspaceRole.ADMIN));
        assertLastOwnerFailure(() -> memberService.remove(ownerId, workspaceId, ownerId));
        assertThat(memberRepository.countByWorkspaceIdAndStatusAndRole(
                workspaceId, MemberStatus.ACTIVE, WorkspaceRole.OWNER)).isEqualTo(1);
    }

    @Test
    void concurrentOwnerDemotionsCannotRemoveEveryOwner() throws Exception {
        UUID ownerOne = activeUser("one@example.com", "Owner One");
        UUID ownerTwo = activeUser("two@example.com", "Owner Two");
        UUID workspaceId = workspaceService.create(
                ownerOne, new CreateWorkspaceRequest("Concurrent Owners", null)).id();
        memberService.add(ownerOne, workspaceId, ownerTwo, WorkspaceRole.OWNER);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Throwable>> attempts = List.of(
                    executor.submit(() -> concurrentDemotion(ready, start, ownerOne, workspaceId)),
                    executor.submit(() -> concurrentDemotion(ready, start, ownerTwo, workspaceId)));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Throwable> outcomes = new ArrayList<>();
            for (Future<Throwable> attempt : attempts) {
                outcomes.add(attempt.get(15, TimeUnit.SECONDS));
            }

            assertThat(outcomes).filteredOn(java.util.Objects::isNull).hasSize(1);
            assertThat(outcomes).filteredOn(java.util.Objects::nonNull).singleElement()
                    .isInstanceOf(BizException.class);
            assertThat(memberRepository.countByWorkspaceIdAndStatusAndRole(
                    workspaceId, MemberStatus.ACTIVE, WorkspaceRole.OWNER)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void workspaceCrudHttpCreatesOwnerAndUpdatesProfile() throws Exception {
        UUID ownerId = activeUser("crud@example.com", "CRUD Owner");
        String response = mockMvc.perform(post("/api/v1/workspaces")
                        .headers(identityHeaders(ownerId, "crud@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", " CRUD Workspace ",
                                "description", "Initial"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("CRUD Workspace"))
                .andExpect(jsonPath("$.data.currentUserRole").value("OWNER"))
                .andReturn().getResponse().getContentAsString();
        UUID workspaceId = UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());

        mockMvc.perform(patch("/api/v1/workspaces/{workspaceId}", workspaceId)
                        .headers(identityHeaders(ownerId, "crud@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Renamed"));
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/members", workspaceId)
                        .headers(identityHeaders(ownerId, "crud@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].role").value("OWNER"));
        mockMvc.perform(patch("/api/v1/users/me")
                        .headers(identityHeaders(ownerId, "crud@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobTitle\":\"Engineer\",\"timezone\":\"Australia/Sydney\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobTitle").value("Engineer"));
    }

    @Test
    void workspaceMemberHttpSupportsAddPatchListAndDeleteWithEmailPrivacy() throws Exception {
        UUID ownerId = activeUser("owner@example.com", "Owner");
        UUID memberId = activeUser("member@example.com", "Member");
        UUID workspaceId = workspaceService.create(
                ownerId, new CreateWorkspaceRequest("Member API", null)).id();

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/members", workspaceId)
                        .headers(identityHeaders(ownerId, "owner@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", memberId,
                                "role", "MEMBER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("MEMBER"));
        mockMvc.perform(patch("/api/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, memberId)
                        .headers(identityHeaders(ownerId, "owner@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"VIEWER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("VIEWER"));
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/members", workspaceId)
                        .headers(identityHeaders(memberId, "member@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].email")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.records[1].email")
                        .value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, memberId)
                        .headers(identityHeaders(ownerId, "owner@example.com")))
                .andExpect(status().isOk());
        assertThat(memberRepository.existsByWorkspaceIdAndUserIdAndStatus(
                workspaceId, memberId, MemberStatus.ACTIVE)).isFalse();
    }

    @Test
    void coreRejectsUnverifiedOrMismatchedInternalIdentityHeaders() throws Exception {
        UUID userId = activeUser("verified@example.com", "Verified");
        var spoofedOnly = new org.springframework.http.HttpHeaders();
        spoofedOnly.set("X-User-Id", userId.toString());
        spoofedOnly.set("X-Username", "verified@example.com");
        mockMvc.perform(get("/api/v1/users/me").headers(spoofedOnly))
                .andExpect(status().isUnauthorized());

        var mismatched = identityHeaders(userId, "verified@example.com");
        mismatched.set("X-User-Id", UUID.randomUUID().toString());
        mockMvc.perform(get("/api/v1/users/me").headers(mismatched))
                .andExpect(status().isUnauthorized());
    }

    private Throwable concurrentDemotion(
            CountDownLatch ready, CountDownLatch start, UUID ownerId, UUID workspaceId) {
        ready.countDown();
        try {
            start.await(5, TimeUnit.SECONDS);
            memberService.changeRole(ownerId, workspaceId, ownerId, WorkspaceRole.MEMBER);
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private void assertLastOwnerFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(CoreErrorCode.WORKSPACE_LAST_OWNER_REQUIRED);
    }

    private UUID activeUser(String email, String displayName) {
        return user("identity.user.registered", email, displayName, AccountStatus.ACTIVE, 1);
    }

    private UUID user(
            String eventType, String email, String displayName, AccountStatus status, long version) {
        UUID userId = UUID.randomUUID();
        projectionService.consume(event(
                UUID.randomUUID(), eventType, userId, email, displayName, status, version));
        return userId;
    }

    private EventEnvelope<IdentityUserEventPayload> event(
            UUID eventId,
            String eventType,
            UUID userId,
            String email,
            String displayName,
            AccountStatus status,
            long sourceVersion) {
        return new EventEnvelope<>(
                eventId,
                eventType,
                1,
                Instant.now(),
                new EventScope(null, null, userId),
                userId,
                new IdentityUserEventPayload(userId, email, displayName, status, sourceVersion),
                Map.of());
    }

    private org.springframework.http.HttpHeaders identityHeaders(UUID userId, String username) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(jwtTokenService.issueAccessToken(
                new CurrentUser(userId, username, java.util.Set.of("USER"))));
        headers.set("X-User-Id", userId.toString());
        headers.set("X-Username", username);
        headers.set("X-User-Roles", "USER");
        return headers;
    }
}
