package uk.deadcatlab.bakbak.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.deadcatlab.bakbak.dto.response.MessageResponse;
import uk.deadcatlab.bakbak.model.Message;

/**
 * Persistence operations for {@link Message}.
 *
 * <p>Message history pagination queries will be added here in later phases.</p>
 */
public interface MessageRepository extends JpaRepository<Message, Long> {

	/**
	 * Cursor-based history page: up to {@code pageable.getPageSize()} messages strictly older than
	 * {@code before}, or the newest messages when {@code before} is {@code null}.
	 *
	 * <p>Rows are returned in {@code created_at DESC} order (newest in this window first). Callers
	 * typically reverse to chronological ascending for API responses.</p>
	 *
	 * <p>Uses {@code created_at < :before} (strict) so the client can safely use the oldest
	 * {@code createdAt} from the previous page as the next cursor without duplicates.</p>
	 */
	@Query("""
			select new uk.deadcatlab.bakbak.dto.response.MessageResponse(
				m.id, m.conversation.id, m.sender.id, m.content, m.createdAt)
			from Message m
			where m.conversation.id = :convId
			  and (:before is null or m.createdAt < :before)
			order by m.createdAt desc
			""")
	List<MessageResponse> findHistoryPage(
		@Param("convId") Long conversationId,
		@Param("before") Instant before,
		Pageable pageable
	);

	/**
	 * Batched fetch of the latest message per conversation for the given conversation ids.
	 *
	 * <p>Within a single conversation, ids are monotonically increasing (BIGSERIAL) and align with
	 * {@code created_at} order, so the row with {@code MAX(id)} is also the most recently created.
	 * This query supports the contact-window listing without N+1 message lookups.</p>
	 */
	@Query("""
			select m.conversation.id as conversationId,
				   m.content as content,
				   m.sender.id as senderId
			from Message m
			where m.id in (
				select max(m2.id) from Message m2
				where m2.conversation.id in :conversationIds
				group by m2.conversation.id
			)
			""")
	List<LastMessageView> findLatestPerConversation(
		@Param("conversationIds") Collection<Long> conversationIds
	);

	/**
	 * Projection of the latest-message preview used by the contact window.
	 */
	interface LastMessageView {
		Long getConversationId();
		String getContent();
		Long getSenderId();
	}
}
