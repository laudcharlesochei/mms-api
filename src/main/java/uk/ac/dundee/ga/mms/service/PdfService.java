package uk.ac.dundee.ga.mms.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import uk.ac.dundee.ga.mms.web.dto.MeetingDtos.MeetingDto;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Renders a meeting as an A4 PDF laid out like the form (FR-18), from a Thymeleaf HTML template. Never stored. */
@Service
public class PdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.of("Europe/London"));

    private final TemplateEngine templates;

    public PdfService(TemplateEngine templates) {
        this.templates = templates;
    }

    public String renderHtml(MeetingDto m) {
        Context ctx = new Context();
        ctx.setVariable("m", m);
        ctx.setVariable("meetingDate", m.meetingDate() == null ? "" : DATE.format(m.meetingDate()));
        ctx.setVariable("nextDate", m.nextMeetingDate() == null ? "Not set" : DATE.format(m.nextMeetingDate()));
        ctx.setVariable("submitted", m.submittedAt() == null ? "Draft - not submitted" : DATETIME.format(m.submittedAt()));
        ctx.setVariable("format", m.format() == null ? "" : switch (m.format()) {
            case IN_PERSON -> "In person";
            case ONLINE -> "Online";
            case PHONE -> "Phone";
        });
        return templates.process("pdf/meeting", ctx);
    }

    public byte[] render(MeetingDto m) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder b = new PdfRendererBuilder();
            b.useFastMode();
            b.withHtmlContent(renderHtml(m), "/");
            b.toStream(out);
            b.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF generation failed", e);
        }
    }
}
