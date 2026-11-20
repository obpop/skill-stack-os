package com.skillstack.importer;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {
    private final ImportService imports;
    public ImportController(ImportService imports) { this.imports = imports; }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportDtos.Job create(Authentication authentication, @RequestPart("file") MultipartFile file) {
        return imports.create(authentication.getName(), file);
    }
    @GetMapping("/{id}")
    public ImportDtos.Job get(Authentication authentication, @PathVariable UUID id) { return imports.get(authentication.getName(), id); }
    @PostMapping("/{id}/accept")
    public ImportDtos.AcceptResponse accept(Authentication authentication, @PathVariable UUID id,
                                             @Valid @RequestBody ImportDtos.AcceptRequest request) {
        return imports.accept(authentication.getName(), id, request);
    }
}
