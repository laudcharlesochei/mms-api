package uk.ac.dundee.ga.mms.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.dundee.ga.mms.auth.CurrentUserService;
import uk.ac.dundee.ga.mms.service.MessagingService;
import uk.ac.dundee.ga.mms.web.dto.AdminDtos.ContactDto;
import uk.ac.dundee.ga.mms.web.dto.AdminDtos.MessageDto;
import uk.ac.dundee.ga.mms.web.dto.AdminDtos.SendMessageRequest;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/messages")
@Tag(name = "Messages", description = "Direct messages between users (from MentorSync)")
public class MessageController {

    private final MessagingService messaging;
    private final CurrentUserService current;

    public MessageController(MessagingService messaging, CurrentUserService current) {
        this.messaging = messaging;
        this.current = current;
    }

    @GetMapping("/contacts")
    public List<ContactDto> contacts() {
        return messaging.contacts(current.get());
    }

    @GetMapping
    public List<MessageDto> conversation(@RequestParam("with") Long withUserId) {
        return messaging.conversation(current.get(), withUserId);
    }

    @PostMapping
    public MessageDto send(@Valid @RequestBody SendMessageRequest r) {
        return messaging.send(current.get(), r.recipientId(), r.body());
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(@PathVariable Long id) {
        messaging.markRead(current.get(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread() {
        return Map.of("unread", messaging.unread(current.get()));
    }
}
