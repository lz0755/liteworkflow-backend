package com.liteworkflow.core.controller;

import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.core.api.PageResult;
import com.liteworkflow.common.security.user.CurrentUser;
import com.liteworkflow.core.application.CommentApplicationService;
import com.liteworkflow.core.dto.request.CreateCommentRequest;
import com.liteworkflow.core.dto.request.UpdateCommentRequest;
import com.liteworkflow.core.dto.response.CommentResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommentController {

    private final CommentApplicationService commentService;

    public CommentController(CommentApplicationService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/api/v1/issues/{issueId}/comments")
    public ApiResponse<PageResult<CommentResponse>> list(
            CurrentUser currentUser,
            @PathVariable UUID issueId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(commentService.list(currentUser.userId(), issueId, page, size));
    }

    @PostMapping("/api/v1/issues/{issueId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentResponse> create(
            CurrentUser currentUser,
            @PathVariable UUID issueId,
            @Valid @RequestBody CreateCommentRequest request) {
        return ApiResponse.success(commentService.create(currentUser.userId(), issueId, request));
    }

    @PatchMapping("/api/v1/comments/{commentId}")
    public ApiResponse<CommentResponse> update(
            CurrentUser currentUser,
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateCommentRequest request) {
        return ApiResponse.success(commentService.update(currentUser.userId(), commentId, request));
    }

    @DeleteMapping("/api/v1/comments/{commentId}")
    public ApiResponse<Void> delete(CurrentUser currentUser, @PathVariable UUID commentId) {
        commentService.delete(currentUser.userId(), commentId);
        return ApiResponse.success();
    }
}
