package com.liteworkflow.core.application;

import com.liteworkflow.common.core.api.PageResult;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.core.domain.UserDirectory;
import com.liteworkflow.core.dto.response.UserSearchResponse;
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

    public UserSearchApplicationService(
            PermissionService permissionService,
            UserDirectoryRepository userDirectoryRepository,
            WorkspaceMemberRepository memberRepository) {
        this.permissionService = permissionService;
        this.userDirectoryRepository = userDirectoryRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public PageResult<UserSearchResponse> search(
            UUID actorId,
            String keyword,
            String contextType,
            UUID workspaceId,
            boolean excludeExistingMembers,
            int page,
            int size) {
        if (!"WORKSPACE".equalsIgnoreCase(contextType)) {
            throw new BizException(CoreErrorCode.USER_SEARCH_CONTEXT_UNSUPPORTED);
        }
        permissionService.requireWorkspaceMemberManager(workspaceId, actorId);
        String normalizedKeyword = normalizeKeyword(keyword);
        validatePage(page, size);
        String pattern = "%" + normalizedKeyword + "%";
        PageRequest pageable = PageRequest.of(page - 1, size);
        Page<UserDirectory> result = excludeExistingMembers
                ? userDirectoryRepository.searchActiveExcludingWorkspaceMembers(pattern, workspaceId, pageable)
                : userDirectoryRepository.searchActive(pattern, pageable);

        List<UUID> resultIds = result.getContent().stream().map(UserDirectory::getUserId).toList();
        Set<UUID> existingMemberIds = resultIds.isEmpty()
                ? Set.of()
                : new HashSet<>(memberRepository.findActiveUserIdsByWorkspaceIdAndUserIdIn(workspaceId, resultIds));
        List<UserSearchResponse> records = result.getContent().stream()
                .map(user -> toResponse(user, existingMemberIds.contains(user.getUserId())))
                .toList();
        log.info(
                "Workspace user search completed workspaceId={}, keywordLength={}, resultCount={}",
                workspaceId,
                keyword.trim().length(),
                records.size());
        return PageResult.of(records, result.getTotalElements(), page, size);
    }

    private UserSearchResponse toResponse(UserDirectory user, boolean existingMember) {
        return new UserSearchResponse(
                user.getUserId(),
                user.getDisplayName(),
                user.getEmailDisplay(),
                user.getAvatarFileId(),
                existingMember,
                !existingMember,
                existingMember ? CoreErrorCode.WORKSPACE_MEMBER_ALREADY_EXISTS.name() : null);
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
