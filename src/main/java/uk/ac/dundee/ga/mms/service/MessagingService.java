package uk.ac.dundee.ga.mms.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.dundee.ga.mms.auth.CurrentUser;
import uk.ac.dundee.ga.mms.config.MmsProperties;
import uk.ac.dundee.ga.mms.domain.AdvisorAllocation;
import uk.ac.dundee.ga.mms.domain.AppUser;
import uk.ac.dundee.ga.mms.domain.Role;
import uk.ac.dundee.ga.mms.domain.StaffMessage;
import uk.ac.dundee.ga.mms.storage.AllocationStore;
import uk.ac.dundee.ga.mms.storage.MessageStore;
import uk.ac.dundee.ga.mms.storage.StudentStore;
import uk.ac.dundee.ga.mms.storage.UserStore;
import uk.ac.dundee.ga.mms.util.TextSanitizer;
import uk.ac.dundee.ga.mms.web.dto.AdminDtos.ContactDto;
import uk.ac.dundee.ga.mms.web.dto.AdminDtos.MessageDto;
import uk.ac.dundee.ga.mms.web.error.ApiException;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Direct messages between MMS users (carried over from the MentorSync messaging module), so an
 * AoS can raise a concern with the Programme Director without leaving the app. Staff can message
 * any active staff member; a student (Phase 2) can message only their Advisor of Studies.
 * Can be switched off with FEATURE_MESSAGING=false.
 */
@Service
public class MessagingService {

    private final MessageStore messages;
    private final UserStore users;
    private final StudentStore students;
    private final AllocationStore allocations;
    private final MailService mail;
    private final MmsProperties props;

    public MessagingService(MessageStore messages, UserStore users, StudentStore students, AllocationStore allocations,
                            MailService mail, MmsProperties props) {
        this.messages = messages;
        this.users = users;
        this.students = students;
        this.allocations = allocations;
        this.mail = mail;
        this.props = props;
    }

    private void requireEnabled() {
        if (!props.getFeatures().isMessaging()) {
            throw ApiException.notFound("Messaging");
        }
    }

    public List<ContactDto> contacts(CurrentUser u) {
        requireEnabled();
        Set<Long> allowed = allowedContactIds(u);
        Map<Long, Long> unread = new HashMap<>();
        Map<Long, Instant> last = new HashMap<>();
        for (StaffMessage m : messages.findInvolving(u.id())) {
            Long other = m.getSenderId().equals(u.id()) ? m.getRecipientId() : m.getSenderId();
            last.merge(other, m.getCreatedAt(), (a, b) -> a.isAfter(b) ? a : b);
            if (m.getRecipientId().equals(u.id()) && m.getReadAt() == null) {
                unread.merge(other, 1L, Long::sum);
            }
        }
        return users.findAll().stream()
                .filter(x -> !x.getId().equals(u.id()) && x.isActive() && (allowed.contains(x.getId()) || last.containsKey(x.getId())))
                .map(x -> new ContactDto(x.getId(), x.getDisplayName(), x.getEmail(), x.getRoles(),
                        unread.getOrDefault(x.getId(), 0L), last.get(x.getId())))
                .sorted(Comparator.comparing(ContactDto::lastMessageAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ContactDto::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public List<MessageDto> conversation(CurrentUser u, Long withUserId) {
        requireEnabled();
        List<StaffMessage> list = messages.conversation(u.id(), withUserId);
        for (StaffMessage m : list) {
            if (m.getRecipientId().equals(u.id()) && m.getReadAt() == null) {
                m.setReadAt(Instant.now());
                messages.save(m);
            }
        }
        return list.stream().map(m -> toDto(m, u)).toList();
    }

    @Transactional
    public MessageDto send(CurrentUser u, Long recipientId, String body) {
        requireEnabled();
        AppUser recipient = users.findById(recipientId).filter(AppUser::isActive)
                .orElseThrow(() -> ApiException.notFound("Recipient"));
        if (!allowedContactIds(u).contains(recipientId)) {
            throw ApiException.forbidden("You cannot message this user.");
        }
        String clean = TextSanitizer.clean(body);
        if (clean == null) {
            throw ApiException.validation(Map.of("body", "Message cannot be empty"));
        }
        StaffMessage m = new StaffMessage();
        m.setSenderId(u.id());
        m.setRecipientId(recipientId);
        m.setBody(clean.length() > 4000 ? clean.substring(0, 4000) : clean);
        m.setCreatedAt(Instant.now());
        StaffMessage saved = messages.save(m);
        mail.newMessage(recipient, u.displayName());
        return toDto(saved, u);
    }

    @Transactional
    public void markRead(CurrentUser u, Long messageId) {
        requireEnabled();
        StaffMessage m = messages.findById(messageId).orElseThrow(() -> ApiException.notFound("Message"));
        if (!m.getRecipientId().equals(u.id())) {
            throw ApiException.forbidden("Not your message");
        }
        if (m.getReadAt() == null) {
            m.setReadAt(Instant.now());
            messages.save(m);
        }
    }

    public long unread(CurrentUser u) {
        return props.getFeatures().isMessaging() ? messages.unreadCount(u.id()) : 0;
    }

    private Set<Long> allowedContactIds(CurrentUser u) {
        Set<Long> ids = new HashSet<>();
        boolean staff = u.hasAny(Role.ADMIN, Role.DIRECTOR, Role.PROG_LEAD, Role.AOS);
        if (staff) {
            users.findAll().stream()
                    .filter(x -> x.isActive() && (x.hasRole(Role.ADMIN) || x.hasRole(Role.DIRECTOR)
                            || x.hasRole(Role.PROG_LEAD) || x.hasRole(Role.AOS)))
                    .forEach(x -> ids.add(x.getId()));
        }
        if (u.has(Role.STUDENT)) {
            students.findByUniEmail(u.email()).ifPresent(st -> allocations.findByStudent(st.getId()).stream()
                    .filter(AdvisorAllocation::isOpen).forEach(a -> ids.add(a.getAosUserId())));
        }
        if (u.canAdvise()) {
            // an AoS may reply to their own advisees who have student accounts
            allocations.findByAos(u.id()).stream().filter(AdvisorAllocation::isOpen)
                    .forEach(a -> students.findById(a.getStudentId()).flatMap(st -> users.findByEmail(st.getUniEmail()))
                            .ifPresent(x -> ids.add(x.getId())));
        }
        ids.remove(u.id());
        return ids;
    }

    private MessageDto toDto(StaffMessage m, CurrentUser u) {
        return new MessageDto(m.getId(), m.getSenderId(), m.getRecipientId(), m.getBody(), m.getCreatedAt(),
                m.getReadAt(), m.getSenderId().equals(u.id()));
    }
}
