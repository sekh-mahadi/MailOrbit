package com.mailorbit.controller;

import com.mailorbit.entity.ImportBatch;
import com.mailorbit.repository.ImportBatchRepository;
import com.mailorbit.service.CsvImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class ImportController {

    private final CsvImportService importService;
    private final ImportBatchRepository batchRepository;

    @PostMapping
    public ResponseEntity<?> importCsv(@RequestParam("file") MultipartFile file) {
        try {
            ImportBatch batch = importService.importCsv(file.getInputStream(), file.getOriginalFilename());
            return ResponseEntity.ok(batch);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Import failed: " + e));
        }
    }

    /** Loads the bundled demo list so the flow can be tried without any file. */
    @PostMapping("/sample")
    public ResponseEntity<?> importSample() {
        try {
            return ResponseEntity.ok(importService.importSample());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Sample import failed: " + e));
        }
    }

    @GetMapping
    public List<ImportBatch> list() {
        return batchRepository.findAllByOrderByIdDesc();
    }
}
