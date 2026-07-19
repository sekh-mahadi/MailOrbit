package com.mailorbit.service;

import com.mailorbit.entity.ContactStatus;
import com.mailorbit.entity.EmailContact;
import com.mailorbit.repository.EmailContactRepository;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EmailContactRepository contactRepository;

    /** @param status a ContactStatus name, or "ALL" */
    public String exportCsv(String status) {
        List<EmailContact> contacts = "ALL".equalsIgnoreCase(status)
                ? contactRepository.findAll()
                : contactRepository.findByStatus(ContactStatus.valueOf(status.toUpperCase()));

        StringWriter out = new StringWriter();
        try (CSVWriter writer = new CSVWriter(out)) {
            writer.writeNext(new String[]{
                    "email", "first_name", "last_name", "company", "source",
                    "status", "score", "reasons", "domain", "verified_at"});
            for (EmailContact c : contacts) {
                writer.writeNext(new String[]{
                        c.getEmail(),
                        nullSafe(c.getFirstName()),
                        nullSafe(c.getLastName()),
                        nullSafe(c.getCompany()),
                        nullSafe(c.getSource()),
                        c.getStatus().name(),
                        c.getScore() == null ? "" : String.valueOf(c.getScore()),
                        nullSafe(c.getReasons()),
                        nullSafe(c.getDomain()),
                        c.getVerifiedAt() == null ? "" : TS.format(c.getVerifiedAt())});
            }
        } catch (Exception e) {
            throw new IllegalStateException("CSV export failed", e);
        }
        return out.toString();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
