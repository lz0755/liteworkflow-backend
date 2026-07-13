package com.liteworkflow.infra.file;

import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.security.user.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FileController {
    private final FileApplicationService service;
    public FileController(FileApplicationService service) { this.service = service; }

    @PostMapping(path = "/api/v1/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FileResponse> upload(CurrentUser user, @RequestParam FilePurpose purpose,
            @RequestParam UUID resourceId, @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(service.upload(user.userId(), purpose, resourceId, file));
    }

    @GetMapping("/api/v1/files/{fileId}")
    public ApiResponse<FileResponse> metadata(CurrentUser user, @PathVariable UUID fileId) {
        return ApiResponse.success(service.metadata(user.userId(), fileId));
    }

    @GetMapping("/api/v1/files/{fileId}/download")
    public ResponseEntity<InputStreamResource> download(CurrentUser user, @PathVariable UUID fileId) {
        FileDownload download = service.download(user.userId(), fileId);
        StoredFile file = download.file();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.getContentType()));
        headers.setContentLength(file.getSizeBytes());
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(file.getOriginalName(), StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.noStore());
        headers.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<>(new InputStreamResource(download.content().content()), headers, HttpStatus.OK);
    }

    @DeleteMapping("/api/v1/files/{fileId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Void> delete(CurrentUser user, @PathVariable UUID fileId) {
        service.delete(user.userId(), fileId);
        return ApiResponse.success();
    }

    @PostMapping(path = "/api/v1/issues/{issueId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FileResponse> attach(CurrentUser user, @PathVariable UUID issueId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(service.upload(user.userId(), FilePurpose.ATTACHMENT, issueId, file));
    }

    @GetMapping("/api/v1/issues/{issueId}/attachments")
    public ApiResponse<List<FileResponse>> attachments(CurrentUser user, @PathVariable UUID issueId) {
        return ApiResponse.success(service.list(user.userId(), FilePurpose.ATTACHMENT, issueId));
    }

    @PostMapping(path = "/api/v1/users/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FileResponse> avatar(CurrentUser user, @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(service.upload(user.userId(), FilePurpose.AVATAR, user.userId(), file));
    }

    @PostMapping(path = "/api/v1/workspaces/{workspaceId}/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FileResponse> workspaceIcon(CurrentUser user, @PathVariable UUID workspaceId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(service.upload(user.userId(), FilePurpose.WORKSPACE_ICON, workspaceId, file));
    }

    @PostMapping(path = "/api/v1/projects/{projectId}/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FileResponse> projectIcon(CurrentUser user, @PathVariable UUID projectId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(service.upload(user.userId(), FilePurpose.PROJECT_ICON, projectId, file));
    }

    @PostMapping(path = "/api/v1/projects/{projectId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FileResponse> projectDocument(CurrentUser user, @PathVariable UUID projectId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(service.upload(user.userId(), FilePurpose.PROJECT_DOCUMENT, projectId, file));
    }

    @PutMapping(path = "/api/v1/projects/{projectId}/documents/{documentId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileResponse> replaceProjectDocument(
            CurrentUser user,
            @PathVariable UUID projectId,
            @PathVariable UUID documentId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(service.replaceProjectDocument(user.userId(), projectId, documentId, file));
    }

    @GetMapping("/api/v1/projects/{projectId}/documents")
    public ApiResponse<List<FileResponse>> projectDocuments(CurrentUser user, @PathVariable UUID projectId) {
        return ApiResponse.success(service.list(user.userId(), FilePurpose.PROJECT_DOCUMENT, projectId));
    }
}
