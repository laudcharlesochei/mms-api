package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.ac.dundee.ga.mms.domain.StaffMessage;

import java.util.List;

public interface MessageRepository extends JpaRepository<StaffMessage, Long> {
    @Query("select m from StaffMessage m where (m.senderId = :a and m.recipientId = :b) or (m.senderId = :b and m.recipientId = :a) order by m.createdAt asc")
    List<StaffMessage> conversation(@Param("a") Long a, @Param("b") Long b);

    List<StaffMessage> findBySenderIdOrRecipientIdOrderByCreatedAtDesc(Long senderId, Long recipientId);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);
}
