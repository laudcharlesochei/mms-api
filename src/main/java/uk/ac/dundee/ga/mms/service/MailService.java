package uk.ac.dundee.ga.mms.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import uk.ac.dundee.ga.mms.config.MmsProperties;
import uk.ac.dundee.ga.mms.domain.AppUser;
import uk.ac.dundee.ga.mms.domain.MentorMeeting;
import uk.ac.dundee.ga.mms.domain.Student;
import uk.ac.dundee.ga.mms.storage.MeetingStore;
import uk.ac.dundee.ga.mms.storage.StudentStore;
import uk.ac.dundee.ga.mms.storage.UserStore;
import uk.ac.dundee.ga.mms.web.dto.MeetingDtos.MeetingDto;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.ProgressRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sends emails asynchronously with retries (FR-19/20, invites, reminders). Disabled unless
 * MAIL_ENABLED=true, in which case messages are only logged (without notes text).
 */
@Slf4j
@Service
public class MailService {

    private final ObjectProvider<JavaMailSender> sender;
    private final TemplateEngine templates;
    private final MmsProperties props;
    private final MeetingStore meetings;
    private final StudentStore students;
    private final UserStore users;
    private final ObjectProvider<MeetingService> meetingService;
    private final PdfService pdf;

    public MailService(ObjectProvider<JavaMailSender> sender, TemplateEngine templates, MmsProperties props,
                       MeetingStore meetings, StudentStore students, UserStore users,
                       ObjectProvider<MeetingService> meetingService, PdfService pdf) {
        this.sender = sender;
        this.templates = templates;
        this.props = props;
        this.meetings = meetings;
        this.students = students;
        this.users = users;
        this.meetingService = meetingService;
        this.pdf = pdf;
    }

    /** On submit: copy to the AoS (and optionally the student) with the PDF attached. */
    @Async
    public void meetingSubmitted(Long meetingId) {
        if (!props.getMail().isEnabled()) {
            log.info("Mail disabled - not sending submit copy for meeting {}", meetingId);
            return;
        }
        MentorMeeting m = meetings.findById(meetingId).orElse(null);
        if (m == null) {
            return;
        }
        AppUser aos = users.findById(m.getAosUserId()).orElse(null);
        Student st = students.findById(m.getStudentId()).orElse(null);
        MeetingDto dto = meetingService.getObject().toDto(m, null);
        List<String> to = new ArrayList<>();
        if (props.getMail().isSendToAos() && aos != null) {
            to.add(aos.getEmail());
        }
        if (props.getMail().isSendToStudent() && st != null && st.getUniEmail() != null) {
            to.add(st.getUniEmail());
        }
        if (to.isEmpty()) {
            return;
        }
        String subject = "Mentor meeting with " + dto.studentName() + " on " + dto.meetingDate() + " recorded";
        String html = render("mail/meeting-submitted", Map.of("m", dto, "link",
                props.getFrontendUrl() + "/meetings/" + meetingId));
        byte[] attachment = props.getMail().isAttachPdf() ? pdf.render(dto) : null;
        for (String recipient : to) {
            send(recipient, subject, html, attachment, "mentor-meeting-" + meetingId + ".pdf");
        }
    }

    @Async
    public void invite(String email, String displayName, String link) {
        if (!props.getMail().isEnabled()) {
            log.info("Mail disabled - invite link for {} is {}", email, link);
            return;
        }
        send(email, "You have been invited to the Mentor Management System",
                render("mail/invite", Map.of("name", displayName, "link", link)), null, null);
    }

    @Async
    public void reminder(AppUser aos, String periodLabel, List<ProgressRow> rows) {
        if (!props.getMail().isEnabled()) {
            log.info("Mail disabled - reminder for {} ({} advisees)", aos.getEmail(), rows.size());
            return;
        }
        send(aos.getEmail(), "Mentor meetings due: " + rows.size() + " advisee(s)",
                render("mail/reminder", Map.of("name", aos.getDisplayName(), "period", periodLabel, "rows", rows,
                        "link", props.getFrontendUrl() + "/advisees")), null, null);
    }

    @Async
    public void newMessage(AppUser recipient, String senderName) {
        if (!props.getMail().isEnabled()) {
            return;
        }
        send(recipient.getEmail(), "New message from " + senderName + " in MMS",
                render("mail/new-message", Map.of("name", recipient.getDisplayName(), "sender", senderName,
                        "link", props.getFrontendUrl() + "/messages")), null, null);
    }

    private String render(String template, Map<String, Object> vars) {
        Context ctx = new Context();
        vars.forEach(ctx::setVariable);
        return templates.process(template, ctx);
    }

    private void send(String to, String subject, String html, byte[] attachment, String attachmentName) {
        JavaMailSender s = sender.getIfAvailable();
        if (s == null) {
            log.warn("No mail sender configured (SMTP_HOST) - message to {} not sent", to);
            return;
        }
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                MimeMessage msg = s.createMimeMessage();
                MimeMessageHelper h = new MimeMessageHelper(msg, attachment != null, "UTF-8");
                h.setFrom(props.getMail().getFrom());
                h.setTo(to);
                h.setSubject(subject);
                h.setText(html, true);
                if (attachment != null) {
                    h.addAttachment(attachmentName, new ByteArrayResource(attachment), "application/pdf");
                }
                s.send(msg);
                return;
            } catch (Exception e) {
                log.warn("Mail attempt {} to {} failed: {}", attempt, to, e.getMessage());
                try {
                    Thread.sleep(1000L * attempt * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("Giving up sending mail to {}", to);
    }
}
