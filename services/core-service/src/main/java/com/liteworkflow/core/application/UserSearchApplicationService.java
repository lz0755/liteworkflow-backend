package com.liteworkflow.core.application;

import com.liteworkflow.common.core.api.PageResult;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.core.domain.UserDirectory;
import com.liteworkflow.core.dto.response.UserSearchResponse;
import com.liteworkflow.core.repository.ProjectMemberRepository;
import com.liteworkflow.core.repository.UserDirectoryRepository;
import com.liteworkflow.core.repository.WorkspaceMemberRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSearchApplicationService {

    private static final Logger log = LoggerFactory.getLogger(UserSearchApplicationService.class);

    private final PermissionService permissionService;
    private final UserDirectoryRepository userDirectoryRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public UserSearchApplicationService(
            PermissionService permissionService,
            UserDirectoryRepository userDirectoryRepository,
            WorkspaceMemberRepository memberRepository,
            ProjectMemberRepository projectMemberRepository) {
        this.permissionService = permissionService;
        this.userDirectoryRepository = userDirectoryRepository;
        this.memberRepository = memberRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    @Transactional(readOnly = true)
    public PageResult<UserSearchResponse> search(
            UUID actorId,
            String keyword,
            String contextType,
            UUID contextId,
            boolean excludeExistingMembers,
            int page,
            int size) {
        String normalizedKeyword = normalizeKeyword(keyword);
        validatePage(page, size);
        String pattern = "%" + normalizedKeyword + "%";
        PageRequest pageable = PageRequest.of(page - 1, size);
        if ("WORKSPACE".equalsIgnoreCase(contextType)) {
            return searchWorkspace(
                    actorId, contextId, keyword, pattern, excludeExistingMembers, page, size, pageable);
        }
        if ("PROJECT".equalsIgnoreCase(contextType)) {
            return searchProject(
                    actorId, contextId, keyword, pattern, excludeExistingMembers, page, size, pageable);
        }
        throw new BizException(CoreErrorCode.USER_SEARCH_CONTEXT_UNSUPPORTED);
    }

    private PageResult<UserSearchResponse> searchWorkspace(
            UUID actorId,
            UUID workspaceId,
            String keyword,
            String pattern,
            boolean excludeExistingMembers,
            int page,
            int size,
            PageRequest pageable) {
        permissionService.requireWorkspaceMemberManager(workspaceId, actorId);
        Page<UserDirectory> result = excludeExistingMembers
                ? userDirectoryRepository.searchActiveExcludingWorkspaceMembers(pattern, workspaceId, pageable)
                : userDirectoryRepository.searchActive(pattern, pageable);
        List<UUID> resultIds = result.getContent().stream().map(UserDirectory::getUserId).toList();
        Set<UUID> existingMemberIds = resultIds.isEmpty()
                ? Set.of()
                : new HashSet<>(memberRepository.findActiveUserIdsByWorkspaceIdAndUserIdIn(workspaceId, resultIds));
        List<UserSearchResponse> records = result.getContent().stream()
                .map(user -> toWorkspaceResponse(user, existingMemberIds.contains(user.getUserId())))
                .toList();
        log.info(
                "Workspace user search completed workspaceId={}, keywordLength={}, resultCount={}",
                workspaceId,
                keyword.trim().length(),
                records.size());
        return PageResult.of(records, result.getTotalElements(), page, size);
    }

    private PageResult<UserSearchResponse> searchProject(
            UUID actorId,
            UUID projectId,
            String keyword,
            String pattern,
            boolean excludeExistingMembers,
            int page,
            int size,
            PageRequest pageable) {
        permissionService.requireProjectMemberManager(projectId, actorId);
        Page<UserDirectory> result = excludeExistingMembers
                ? userDirectoryRepository.searchActiveWorkspaceMembersExcludingProjectMembers(
                        pattern, projectId, pageable)
                : userDirectoryRepository.searchActiveWorkspaceMembersForProject(pattern, projectId, pageable);
        List<UUID> resultIds = result.getContent().stream().map(UserDirectory::getUserId).toList();
        Set<UUID> existingMemberIds = resultIds.isEmpty()
                ? Set.of()
                : new HashSet<>(projectMemberRepository.findActiveUserIdsByProjectIdAndUserIdIn(
                        projectId, resultIds));
        List<UserSearchResponse> records = result.getContent().stream()
                .map(user -> toProjectResponse(user, existingMemberIds.contains(user.getUserId())))
                .toList();
        log.info(
                "Project user search completed projectId={}, keywordLength={}, resultCount={}",
                projectId,
                keyword.trim().length(),
                records.size());
        return PageResult.of(records, result.getTotalElements(), page, size);
    }

    private UserSearchResponse toWorkspaceResponse(UserDirectory user, boolean existingMember) {
        return new UserSearchResponse(
                user.getUserId(),
                user.getDisplayName(),
                user.getEmailDisplay(),
                user.getAvatarFileId(),
                existingMember,
                false,
                !existingMember,
                existingMember ? CoreErrorCode.WORKSPACE_MEMBER_ALREADY_EXISTS.name() : null);
    }

    private UserSearchResponse toProjectResponse(UserDirectory user, boolean existingMember) {
        return new UserSearchResponse(
                user.getUserId(),
                user.getDisplayName(),
                user.getEmailDisplay(),
                user.getAvatarFileId(),
                true,
                existingMember,
                !existingMember,
                existingMember ? CoreErrorCode.PROJECT_MEMBER_ALREADY_EXISTS.name() : null);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            throw new BizException(CoreErrorCode.USER_SEARCH_KEYWORD_TOO_SHORT);
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT)
                .replace("%", "")
                .replace("_", "");
        int minimumLength = normalized.contains("@") ? 3 : 2;
        if (normalized.length() < minimumLength) {
            throw new BizException(CoreErrorCode.USER_SEARCH_KEYWORD_TOO_SHORT);
        }
        return normalized;
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 50) {
            throw new BizException(
                    com.liteworkflow.common.core.error.CommonErrorCode.VALIDATION_ERROR,
                    "page must be at least 1 and size must be between 1 and 50");
        }
    }
}
