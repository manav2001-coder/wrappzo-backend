package com.wrapzzo.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrapzzo.backend.service.EmailSender;
import com.wrapzzo.backend.service.WorkflowEmailSender;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two rules about sending email, each learnt the hard way.
 *
 * <p>1. Sending means INSERTing into the email outbox, and Postgres refuses an INSERT inside a read-only transaction. A service that
 * holds a sender but marks a method readOnly fails on every real address -- and, because the listeners swallow the error to protect the
 * user's action, nobody notices: vendor and approver emails were silently never queued in production until this test.
 *
 * <p>2. The order-workflow services (their names end in NotificationService or ReminderService) send through
 * {@link WorkflowEmailSender}, never {@link EmailSender} directly: that is the one place the platform-wide email switch is checked, so
 * going round it would make an email the Super Admin cannot switch off.
 */
class EmailSendingIsNeverReadOnlyTest {

    /** Methods that are genuinely read-only and never send, though their class does. Add one only after checking its body. */
    private static final Set<String> PURE_READS = Set.of("VendorService.list", "VendorService.view");

    private static List<Class<?>> applicationClasses() throws Exception {
        Path root = Path.of(EmailSender.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<Class<?>> classes = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root.resolve("com/wrapzzo/backend"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".class")).toList()) {
                String name = root.relativize(file).toString().replace(File.separatorChar, '.').replaceAll("\\.class$", "");
                try {
                    classes.add(Class.forName(name, false, EmailSendingIsNeverReadOnlyTest.class.getClassLoader()));
                } catch (Throwable ignored) {
                    // not loadable on its own: not ours to check
                }
            }
        }
        return classes;
    }

    private static boolean holds(Class<?> type, Class<?> sender) {
        for (Field field : type.getDeclaredFields()) {
            if (sender.isAssignableFrom(field.getType())) return true;
        }
        return false;
    }

    @Test
    void noServiceThatSendsEmailHasAReadOnlyTransactionalMethod() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Class<?> type : applicationClasses()) {
            if (!holds(type, EmailSender.class) && !holds(type, WorkflowEmailSender.class)) continue;
            Transactional onClass = type.getAnnotation(Transactional.class);
            if (onClass != null && onClass.readOnly()) offenders.add(type.getSimpleName() + " (whole class)");
            for (Method m : type.getDeclaredMethods()) {
                Transactional t = m.getAnnotation(Transactional.class);
                if (t != null && t.readOnly() && !PURE_READS.contains(type.getSimpleName() + "." + m.getName())) {
                    offenders.add(type.getSimpleName() + "." + m.getName());
                }
            }
        }
        assertThat(offenders).as("methods that are read-only but live in a class that sends email").isEmpty();
    }

    @Test
    void theOrderWorkflowServicesSendThroughTheSwitchNeverAroundIt() throws Exception {
        List<String> around = new ArrayList<>();
        List<String> workflow = new ArrayList<>();
        for (Class<?> type : applicationClasses()) {
            String name = type.getSimpleName();
            if (!(name.endsWith("NotificationService") || name.endsWith("ReminderService"))) continue;
            workflow.add(name);
            if (holds(type, EmailSender.class)) around.add(name);
        }
        assertThat(workflow).as("the workflow services this rule is about").isNotEmpty();
        assertThat(around).as("workflow services holding EmailSender directly instead of WorkflowEmailSender").isEmpty();
    }
}
