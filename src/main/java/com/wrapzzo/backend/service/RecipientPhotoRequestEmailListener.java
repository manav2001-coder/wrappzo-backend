package com.wrapzzo.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs after the approval transaction actually commits (TransactionPhase.AFTER_COMMIT) --
 * never send a link for a token that ended up not persisting -- and off the request
 * thread (@Async), so approving a submission doesn't wait on the Resend call. See
 * AsyncConfig for the executor this runs on.
 */
@Component
@RequiredArgsConstructor
public class RecipientPhotoRequestEmailListener {

    private final EmailSender emailSender;

    @Async("emailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecipientPhotoRequested(RecipientPhotoRequestedEvent event) {
        PendingRecipientPhotoRequest r = event.request();
        emailSender.send(
                r.recipientEmail(),
                r.companyName() + " has a gift for you!",
                """
                <div style="font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;max-width:480px;margin:0 auto;padding:32px 24px;">
                  <p style="font-size:15px;color:#111827;">Hi %s,</p>
                  <p style="font-size:15px;color:#111827;line-height:1.6;">
                    <strong>%s</strong> has arranged a gift for you: <strong>%s</strong>.
                    We just need a quick photo from you to complete the delivery.
                  </p>
                  <p style="text-align:center;margin:28px 0;">
                    <a href="%s" style="display:inline-block;padding:12px 28px;background:#4f46e5;color:#ffffff;text-decoration:none;border-radius:8px;font-weight:600;font-size:15px;">
                      Upload your photo
                    </a>
                  </p>
                  <p style="font-size:12px;color:#9ca3af;">This link expires in %d hours and can only be used once.</p>
                </div>
                """
                        .formatted(r.recipientName(), r.companyName(), r.giftName(), r.uploadUrl(), r.expiryHours()));
    }
}
