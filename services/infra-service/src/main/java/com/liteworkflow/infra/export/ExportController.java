package com.liteworkflow.infra.export;

import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.security.user.CurrentUser;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExportController {

    private final ExportApplicationService exports;

    public ExportController(ExportApplicationService exports) {
        this.exports = exports;
    }

    @PostMapping("/api/v1/exports/issues")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ExportJobResponse> create(
            CurrentUser user,
            @Valid @RequestBody CreateIssueExportRequest request) {
        return ApiResponse.success(exports.create(user.userId(), request));
    }

    @GetMapping("/api/v1/exports/{jobId}")
    public ApiResponse<ExportJobResponse> get(CurrentUser user, @PathVariable UUID jobId) {
        return ApiResponse.success(exports.get(user.userId(), jobId));
    }

    @GetMapping("/api/v1/exports/{jobId}/download")
    public ResponseEntity<InputStreamResource> download(CurrentUser user, @PathVariable UUID jobId) {
        ExportDownload download = exports.download(user.userId(), jobId);
        ExportFile file = download.file();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.getContentType()));
        headers.setContentLength(file.getSizeBytes());
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(file.getFileName(), StandardCharsets.UTF_8)
                .build());
        headers.setCacheControl(CacheControl.noStore());
        headers.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<>(
                new InputStreamResource(download.content().content()), headers, HttpStatus.OK);
    }
}
