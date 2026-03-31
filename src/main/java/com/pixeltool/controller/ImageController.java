package com.pixeltool.controller;

import com.pixeltool.dto.ImageEditOptions;
import com.pixeltool.dto.PreviewResponse;
import com.pixeltool.dto.ProcessOptions;
import com.pixeltool.dto.ProcessResponse;
import com.pixeltool.service.ImageBatchService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageBatchService imageBatchService;

    public ImageController(ImageBatchService imageBatchService) {
        this.imageBatchService = imageBatchService;
    }

    @PostMapping("/preview")
    public PreviewResponse preview(@RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "options", required = false) String optionsJson,
                                   @RequestParam(value = "edit", required = false) String editJson) throws IOException {
        ProcessOptions options = imageBatchService.parseOptions(optionsJson);
        List<ImageEditOptions> edits = imageBatchService.parseEdits(editJson == null ? "[]" : "[" + editJson + "]");
        ImageEditOptions editOptions = edits.isEmpty() ? new ImageEditOptions() : edits.get(0);
        return imageBatchService.previewFile(file, options, editOptions);
    }

    @PostMapping("/process")
    public ProcessResponse process(@RequestParam("files") MultipartFile[] files,
                                   @RequestParam(value = "options", required = false) String optionsJson,
                                   @RequestParam(value = "optionsList", required = false) String optionsListJson,
                                   @RequestParam(value = "edits", required = false) String editsJson) throws IOException {
        ProcessOptions options = imageBatchService.parseOptions(optionsJson);
        List<ProcessOptions> optionsList = imageBatchService.parseOptionsList(optionsListJson);
        List<ImageEditOptions> edits = imageBatchService.parseEdits(editsJson);
        return imageBatchService.processFiles(files, options, edits, optionsList);
    }

    @GetMapping("/jobs/{jobId}/files/{fileName:.+}")
    public ResponseEntity<Resource> fileRoot(@PathVariable String jobId,
                                         @PathVariable String fileName) throws IOException {
        Path path = imageBatchService.resolveJobFile(jobId, fileName);
        return buildFileResponse(path, MediaType.IMAGE_PNG, false);
    }

    @GetMapping("/jobs/{jobId}/files/{folder}/{fileName:.+}")
    public ResponseEntity<Resource> file(@PathVariable String jobId,
                                         @PathVariable String folder,
                                         @PathVariable String fileName) throws IOException {
        Path path = imageBatchService.resolveNestedFile(jobId, folder, fileName);
        return buildFileResponse(path, MediaType.IMAGE_PNG, false);
    }

    @GetMapping("/jobs/{jobId}/download")
    public ResponseEntity<Resource> download(@PathVariable String jobId) throws IOException {
        Path path = imageBatchService.resolveZip(jobId);
        return buildFileResponse(path, MediaType.APPLICATION_OCTET_STREAM, true);
    }

    private ResponseEntity<Resource> buildFileResponse(Path path, MediaType mediaType, boolean attachment) throws IOException {
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }
        FileSystemResource resource = new FileSystemResource(path);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        if (attachment) {
            headers.setContentDisposition(ContentDisposition.attachment().filename(path.getFileName().toString()).build());
        }
        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(resource.contentLength())
                .body(resource);
    }
}
