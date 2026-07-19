package com.mailorbit.service;

import com.mailorbit.entity.ContactStatus;
import com.mailorbit.entity.EmailContact;
import com.mailorbit.entity.ImportBatch;
import com.mailorbit.repository.EmailContactRepository;
import com.mailorbit.repository.ImportBatchRepository;
import com.mailorbit.util.EmailNormalizer;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * CSV -> EmailContact ingestion with flexible header detection and
 * dedupe-on-import. Rows whose dedupe key already exists are counted
 * as duplicates and skipped; rows without a parseable address are
 * counted as unparseable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportService {

    private static final List<String> EMAIL_HEADERS = List.of(
            "email", "e-mail", "mail", "email address", "email_address", "emailaddress", "work email");
    private static final List<String> FIRST_HEADERS = List.of(
            "first_name", "firstname", "first name", "first", "fname", "given name", "given_name");
    private static final List<String> LAST_HEADERS = List.of(
            "last_name", "lastname", "last name", "last", "lname", "surname", "family name", "family_name");
    private static final List<String> NAME_HEADERS = List.of(
            "name", "full name", "full_name", "fullname", "contact name", "contact");
    private static final List<String> COMPANY_HEADERS = List.of(
            "company", "company name", "company_name", "organization", "organisation", "employer", "account");
    private static final List<String> SOURCE_HEADERS = List.of(
            "source", "origin", "list", "campaign");

    private final EmailContactRepository contactRepository;
    private final ImportBatchRepository batchRepository;

    public ImportBatch importCsv(InputStream in, String fileName) throws Exception {
        try (CSVReader reader = new CSVReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("CSV file is empty");
            }

            String[] header = rows.get(0);
            stripBom(header);
            int emailCol = findColumn(header, EMAIL_HEADERS);
            int firstCol = findColumn(header, FIRST_HEADERS);
            int lastCol = findColumn(header, LAST_HEADERS);
            int nameCol = findColumn(header, NAME_HEADERS);
            int companyCol = findColumn(header, COMPANY_HEADERS);
            int sourceCol = findColumn(header, SOURCE_HEADERS);

            int firstDataRow = 1;
            if (emailCol < 0) {
                // no recognizable header: look for an '@' column in the first row and treat the file as headerless
                emailCol = detectEmailColumn(header);
                if (emailCol < 0 && rows.size() > 1) {
                    emailCol = detectEmailColumn(rows.get(1));
                } else if (emailCol >= 0) {
                    firstDataRow = 0;
                }
                if (emailCol < 0) {
                    throw new IllegalArgumentException(
                            "Could not find an email column (looked for headers like 'email' and for '@' values)");
                }
            }

            ImportBatch batch = new ImportBatch();
            batch.setFileName(fileName);
            batch = batchRepository.save(batch);

            int total = 0, imported = 0, duplicates = 0, unparseable = 0;
            for (int i = firstDataRow; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (row.length == 0 || (row.length == 1 && row[0].isBlank())) continue;
                total++;

                String email = EmailNormalizer.normalize(cell(row, emailCol));
                if (email == null) {
                    unparseable++;
                    continue;
                }
                String dedupeKey = EmailNormalizer.dedupeKey(email);
                if (contactRepository.existsByDedupeKey(dedupeKey)) {
                    duplicates++;
                    continue;
                }

                EmailContact contact = new EmailContact();
                contact.setEmail(email);
                contact.setDedupeKey(dedupeKey);
                contact.setDomain(EmailNormalizer.domainOf(email));
                contact.setBatchId(batch.getId());
                contact.setStatus(ContactStatus.NEW);

                String first = cell(row, firstCol);
                String last = cell(row, lastCol);
                if ((first == null || first.isBlank()) && nameCol >= 0) {
                    String full = cell(row, nameCol);
                    if (full != null && !full.isBlank()) {
                        String[] parts = full.trim().split("\\s+", 2);
                        first = parts[0];
                        if (parts.length > 1 && (last == null || last.isBlank())) last = parts[1];
                    }
                }
                contact.setFirstName(blankToNull(first));
                contact.setLastName(blankToNull(last));
                contact.setCompany(blankToNull(cell(row, companyCol)));
                String source = blankToNull(cell(row, sourceCol));
                contact.setSource(source != null ? source : fileName);

                contactRepository.save(contact);
                imported++;
            }

            batch.setTotalRows(total);
            batch.setImported(imported);
            batch.setDuplicates(duplicates);
            batch.setUnparseable(unparseable);
            batch = batchRepository.save(batch);
            log.info("Imported {}: {} rows, {} new, {} duplicates, {} unparseable",
                    fileName, total, imported, duplicates, unparseable);
            return batch;
        }
    }

    /** Imports the bundled demo list (samples/sample-contacts.csv). */
    public ImportBatch importSample() throws Exception {
        try (InputStream in = new ClassPathResource("samples/sample-contacts.csv").getInputStream()) {
            return importCsv(in, "sample-contacts.csv");
        }
    }

    private static void stripBom(String[] header) {
        if (header.length > 0 && header[0] != null && header[0].startsWith("﻿")) {
            header[0] = header[0].substring(1);
        }
    }

    private static int findColumn(String[] header, List<String> candidates) {
        for (int i = 0; i < header.length; i++) {
            String h = header[i] == null ? "" : header[i].trim().toLowerCase(Locale.ROOT);
            if (candidates.contains(h)) return i;
        }
        return -1;
    }

    private static int detectEmailColumn(String[] row) {
        for (int i = 0; i < row.length; i++) {
            if (row[i] != null && row[i].contains("@")) return i;
        }
        return -1;
    }

    private static String cell(String[] row, int col) {
        return col >= 0 && col < row.length ? row[col] : null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
