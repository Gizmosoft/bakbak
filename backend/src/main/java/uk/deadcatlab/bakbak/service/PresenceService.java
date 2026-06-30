package uk.deadcatlab.bakbak.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.deadcatlab.bakbak.dto.PresenceStatus;
import uk.deadcatlab.bakbak.dto.response.PresenceEvent;
import uk.deadcatlab.bakbak.model.UserPresence;
import uk.deadcatlab.bakbak.repository.UserPresenceRepository;

/**
 * Tracks and broadcasts user online/offline state for store-and-forward routing.
 */
@Service
public class PresenceService {

	private static final String PRESENCE_TOPIC_PREFIX = "/topic/presence/";

	private final UserPresenceRepository presenceRepository;
	private final SimpMessagingTemplate messagingTemplate;

	public PresenceService(
		UserPresenceRepository presenceRepository,
		SimpMessagingTemplate messagingTemplate
	) {
		this.presenceRepository = presenceRepository;
		this.messagingTemplate = messagingTemplate;
	}

	@Transactional
	public void markOnline(Long userId, String sessionId) {
		UserPresence presence = presenceRepository.findByUserId(userId)
			.orElseGet(() -> UserPresence.builder().userId(userId).build());
		presence.setStatus(PresenceStatus.ONLINE);
		presence.setSessionId(sessionId);
		presence.setLastSeenAt(Instant.now());
		presenceRepository.save(presence);
		broadcast(userId, PresenceStatus.ONLINE);
	}

	/**
	 * Marks offline only when the disconnecting {@code sessionId} matches the active session,
	 * so a stale disconnect does not clobber a newer connection.
	 */
	@Transactional
	public void markOffline(Long userId, String sessionId) {
		presenceRepository.findByUserId(userId).ifPresent(presence -> {
			if (sessionId != null
				&& presence.getSessionId() != null
				&& !sessionId.equals(presence.getSessionId())) {
				return;
			}
			presence.setStatus(PresenceStatus.OFFLINE);
			presence.setLastSeenAt(Instant.now());
			presenceRepository.save(presence);
			broadcast(userId, PresenceStatus.OFFLINE);
		});
	}

	@Transactional
	public void heartbeat(Long userId) {
		presenceRepository.findByUserId(userId).ifPresent(presence -> {
			presence.setLastSeenAt(Instant.now());
			presenceRepository.save(presence);
		});
	}

	@Transactional(readOnly = true)
	public boolean isOnline(Long userId) {
		return presenceRepository.findByUserId(userId)
			.map(p -> p.getStatus() == PresenceStatus.ONLINE)
			.orElse(false);
	}

	@Transactional(readOnly = true)
	public Map<Long, PresenceStatus> getPresence(List<Long> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, PresenceStatus> result = new HashMap<>();
		for (Long userId : userIds) {
			result.put(userId, PresenceStatus.OFFLINE);
		}
		for (UserPresence row : presenceRepository.findAllByUserIdIn(userIds)) {
			result.put(row.getUserId(), row.getStatus());
		}
		return result;
	}

	private void broadcast(Long userId, PresenceStatus status) {
		PresenceEvent event = new PresenceEvent(userId, status, Instant.now());
		messagingTemplate.convertAndSend(PRESENCE_TOPIC_PREFIX + userId, event);
	}
}
