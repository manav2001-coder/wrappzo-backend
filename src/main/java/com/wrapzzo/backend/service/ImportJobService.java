package com.wrapzzo.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wrapzzo.backend.dto.BulkDoctorRow;
import com.wrapzzo.backend.dto.BulkDoctorsResponse;
import com.wrapzzo.backend.dto.BulkImportUserRow;
import com.wrapzzo.backend.dto.BulkImportUsersResponse;
import com.wrapzzo.backend.dto.ImportJobResponse;
import com.wrapzzo.backend.exception.ImportJobNotFoundException;
import com.wrapzzo.backend.exception.UserImportValidationException;
import com.wrapzzo.backend.security.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Bulk uploads of users and doctors. A small sheet is done inside the request, as it always was. A large one is
 * checked in the request -- so a bad row is still reported at once -- then saved for a background worker, which does
 * the writes as the person who uploaded it, all or nothing. The screen follows it with {@link #get}.
 */
@Service
@Slf4j
public class ImportJobService {

    /** Sheets up to this size are done in the request: it takes a moment, and the answer comes back at once. */
    static final int INLINE_LIMIT = 100;
    static final String USERS = "USERS";
    static final String DOCTORS = "DOCTORS";

    private static final ObjectMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    private final ImportJobStore jobs;
    private final UserService users;
    private final RecipientService recipients;

    public ImportJobService(ImportJobStore jobs, UserService users, RecipientService recipients) {
        this.jobs = jobs;
        this.users = users;
        this.recipients = recipients;
    }

    public ImportJobResponse importUsers(UUID companyId, List<BulkImportUserRow> rows) {
        if (rows.size() <= INLINE_LIMIT) {
            BulkImportUsersResponse done = users.bulkImport(companyId, rows);
            return completed(USERS, rows.size(), Map.of("created", done.createdCount(), "updated", done.updatedCount()));
        }
        users.validateBulkImport(companyId, rows);
        return queued(USERS, companyId, rows.size(), toJson(rows));
    }

    public ImportJobResponse importDoctors(List<BulkDoctorRow> rows) {
        if (rows.size() <= INLINE_LIMIT) {
            BulkDoctorsResponse done = recipients.bulkCreateMine(rows);
            return completed(DOCTORS, rows.size(), Map.of("created", done.created(), "joined", done.joined(), "unchanged", done.unchanged()));
        }
        return queued(DOCTORS, null, rows.size(), toJson(rows));
    }

    /** 200 with the answer when it is done, 202 Accepted when it is still to come. */
    public ResponseEntity<ImportJobResponse> respond(ImportJobResponse job) {
        return ResponseEntity.status("COMPLETED".equals(job.status()) ? HttpStatus.OK : HttpStatus.ACCEPTED).body(job);
    }

    /** Only the person who uploaded the sheet can follow it. */
    public ImportJobResponse get(UUID id) {
        ImportJobStore.Row row = jobs.find(id).filter(job -> job.requestedBy().equals(CurrentUser.id())).orElseThrow(() -> new ImportJobNotFoundException(id));
        Map<String, Integer> counts = row.result() == null ? Map.of() : fromJson(row.result(), new TypeReference<>() { });
        List<String> errors = row.errors() == null ? List.of() : fromJson(row.errors(), new TypeReference<>() { });
        return new ImportJobResponse(row.id(), row.kind(), row.status(), row.totalRows(), counts, errors, row.message());
    }

    /** Runs a claimed job as the person who uploaded it, and records how it ended. Never throws. */
    void execute(ImportJobStore.Claimed job) {
        Authentication callers = SecurityContextHolder.getContext().getAuthentication();
        SecurityContext asUploader = SecurityContextHolder.createEmptyContext();
        asUploader.setAuthentication(new UsernamePasswordAuthenticationToken(job.requestedBy(), null, List.of()));
        SecurityContextHolder.setContext(asUploader);
        try {
            Map<String, Integer> counts = switch (job.kind()) {
                case USERS -> {
                    BulkImportUsersResponse done = users.bulkImport(job.companyId(), fromJson(job.payload(), new TypeReference<List<BulkImportUserRow>>() { }));
                    yield Map.of("created", done.createdCount(), "updated", done.updatedCount());
                }
                case DOCTORS -> {
                    BulkDoctorsResponse done = recipients.bulkCreateMine(fromJson(job.payload(), new TypeReference<List<BulkDoctorRow>>() { }));
                    yield Map.of("created", done.created(), "joined", done.joined(), "unchanged", done.unchanged());
                }
                default -> throw new IllegalStateException("Unknown import kind " + job.kind());
            };
            jobs.markCompleted(job.id(), toJson(counts));
        } catch (UserImportValidationException e) {
            // The sheet was fine when uploaded but something it depends on changed while it waited.
            jobs.markFailed(job.id(), "Import validation failed -- fix these rows and re-upload. Nothing was imported.", toJson(e.getErrors()));
        } catch (RuntimeException e) {
            log.warn("Import {} ({}) failed: {}", job.id(), job.kind(), e.getMessage(), e);
            String reason = e.getMessage() != null ? e.getMessage() : "an unexpected " + e.getClass().getSimpleName();
            jobs.markFailed(job.id(), "The import failed: " + reason + ". Nothing was imported.", null);
        } finally {
            SecurityContext restored = SecurityContextHolder.createEmptyContext();
            restored.setAuthentication(callers);
            SecurityContextHolder.setContext(restored);
        }
    }

    private ImportJobResponse queued(String kind, UUID companyId, int totalRows, String payload) {
        UUID id = jobs.create(kind, companyId, CurrentUser.id(), totalRows, payload);
        return new ImportJobResponse(id, kind, "QUEUED", totalRows, Map.of(), List.of(), null);
    }

    private static ImportJobResponse completed(String kind, int totalRows, Map<String, Integer> counts) {
        return new ImportJobResponse(null, kind, "COMPLETED", totalRows, counts, List.of(), null);
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not save the import: " + e.getOriginalMessage(), e);
        }
    }

    private static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return JSON.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read the saved import: " + e.getOriginalMessage(), e);
        }
    }
}
