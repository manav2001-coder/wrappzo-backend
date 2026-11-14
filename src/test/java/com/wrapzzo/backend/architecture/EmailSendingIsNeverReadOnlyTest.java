package com.wrapzzo.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrapzzo.backend.service.EmailSender;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sending an email means INSERTing into the email outbox, and Postgres refuses an INSERT inside a read-only transaction. A service that
 * holds an EmailSender but marks a method readOnly therefore fails on every real address -- and, because the listeners swallow the error
 * to protect the user's action, nobody notices: vendor and approver emails were silently never queued in production until this test.
 */
class EmailSendingIsNeverReadOnlyTest {

    /** Methods that are genuinely read-only and never send, though their class does. Add one only after checking its body. */
    private static final java.util.Set<String> PURE_READS = java.util.Set.of("VendorService.list", "VendorService.view");

    @Test
    void noServiceThatSendsEmailHasAReadOnlyTransactionalMethod() throws Exception {
        Path root = Path.of(EmailSender.class.getProtectionDomain().getCodeSource().getLocation().toURI()).resolve("com/wrapzzo/backend");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".class")).toList()) {
                String name = root.getParent().getParent().getParent().relativize(file).toString().replace(File.separatorChar, '.').replaceAll("\\.class$", "");
                Class<?> type;
                try {
                    type = Class.forName(name, false, getClass().getClassLoader());
                } catch (Throwable e) {
                    continue;
                }
                boolean sendsEmail = false;
                for (var field : type.getDeclaredFields()) {
                    if (EmailSender.class.isAssignableFrom(field.getType())) sendsEmail = true;
                }
                if (!sendsEmail) continue;
                boolean classReadOnly = type.isAnnotationPresent(Transactional.class) && type.getAnnotation(Transactional.class).readOnly();
                if (classReadOnly) offenders.add(type.getSimpleName() + " (whole class)");
                for (Method m : type.getDeclaredMethods()) {
                    Transactional t = m.getAnnotation(Transactional.class);
                    if (t != null && t.readOnly() && !PURE_READS.contains(type.getSimpleName() + "." + m.getName())) offenders.add(type.getSimpleName() + "." + m.getName());
                }
            }
        }
        assertThat(offenders).as("methods that are read-only but live in a class that sends email").isEmpty();
    }
}
