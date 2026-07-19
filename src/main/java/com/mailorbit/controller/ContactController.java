package com.mailorbit.controller;

import com.mailorbit.entity.ContactStatus;
import com.mailorbit.entity.EmailContact;
import com.mailorbit.repository.EmailContactRepository;
import com.mailorbit.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final EmailContactRepository contactRepository;
    private final ExportService exportService;

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "id"));
        boolean hasStatus = status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status);
        boolean hasQuery = q != null && !q.isBlank();

        Page<EmailContact> result;
        if (hasStatus && hasQuery) {
            result = contactRepository.findByStatusAndEmailContainingIgnoreCase(
                    ContactStatus.valueOf(status.toUpperCase()), q.trim(), pageable);
        } else if (hasStatus) {
            result = contactRepository.findByStatus(ContactStatus.valueOf(status.toUpperCase()), pageable);
        } else if (hasQuery) {
            result = contactRepository.findByEmailContainingIgnoreCase(q.trim(), pageable);
        } else {
            result = contactRepository.findAll(pageable);
        }

        return Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", result.getNumber(),
                "size", result.getSize());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable long id) {
        if (!contactRepository.existsById(id)) return ResponseEntity.notFound().build();
        contactRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    /** @param status ContactStatus name or ALL */
    @GetMapping("/export")
    public ResponseEntity<String> export(@RequestParam(defaultValue = "VALID") String status) {
        String csv = exportService.exportCsv(status);
        String fileName = "mailorbit_" + status.toLowerCase() + "_" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
