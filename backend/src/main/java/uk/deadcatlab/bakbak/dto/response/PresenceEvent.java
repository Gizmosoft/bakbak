package uk.deadcatlab.bakbak.dto.response;

import java.time.Instant;
import uk.deadcatlab.bakbak.dto.PresenceStatus;

/**
 * Presence update broadcast when a user connects, disconnects, or changes session state.
 */
public record PresenceEvent(
	Long userId,
	PresenceStatus status,
	Instant timestamp
) {}
