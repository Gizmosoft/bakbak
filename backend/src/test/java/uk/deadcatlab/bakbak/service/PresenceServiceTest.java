package uk.deadcatlab.bakbak.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import uk.deadcatlab.bakbak.dto.PresenceStatus;
import uk.deadcatlab.bakbak.dto.response.PresenceEvent;
import uk.deadcatlab.bakbak.model.UserPresence;
import uk.deadcatlab.bakbak.repository.UserPresenceRepository;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

	@Mock UserPresenceRepository presenceRepository;
	@Mock SimpMessagingTemplate messagingTemplate;

	PresenceService presenceService;

	@BeforeEach
	void setUp() {
		presenceService = new PresenceService(presenceRepository, messagingTemplate);
	}

	@Test
	void markOnline_upsertsAndBroadcasts() {
		when(presenceRepository.findByUserId(7L)).thenReturn(Optional.empty());

		presenceService.markOnline(7L, "session-a");

		ArgumentCaptor<UserPresence> captor = ArgumentCaptor.forClass(UserPresence.class);
		verify(presenceRepository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(PresenceStatus.ONLINE);
		assertThat(captor.getValue().getSessionId()).isEqualTo("session-a");

		ArgumentCaptor<PresenceEvent> eventCaptor = ArgumentCaptor.forClass(PresenceEvent.class);
		verify(messagingTemplate).convertAndSend(eq("/topic/presence/7"), eventCaptor.capture());
		assertThat(eventCaptor.getValue().status()).isEqualTo(PresenceStatus.ONLINE);
	}

	@Test
	void markOffline_ignoresStaleSessionDisconnect() {
		UserPresence active = UserPresence.builder()
			.userId(7L)
			.status(PresenceStatus.ONLINE)
			.sessionId("session-new")
			.build();
		when(presenceRepository.findByUserId(7L)).thenReturn(Optional.of(active));

		presenceService.markOffline(7L, "session-old");

		verify(presenceRepository, never()).save(any(UserPresence.class));
		verify(messagingTemplate, never()).convertAndSend(any(String.class), any(PresenceEvent.class));
	}

	@Test
	void markOffline_matchingSessionMarksOffline() {
		UserPresence active = UserPresence.builder()
			.userId(7L)
			.status(PresenceStatus.ONLINE)
			.sessionId("session-a")
			.build();
		when(presenceRepository.findByUserId(7L)).thenReturn(Optional.of(active));

		presenceService.markOffline(7L, "session-a");

		ArgumentCaptor<UserPresence> captor = ArgumentCaptor.forClass(UserPresence.class);
		verify(presenceRepository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(PresenceStatus.OFFLINE);
	}

	@Test
	void isOnline_returnsFalseWhenAbsent() {
		when(presenceRepository.findByUserId(4L)).thenReturn(Optional.empty());
		assertThat(presenceService.isOnline(4L)).isFalse();
	}

	@Test
	void getPresence_defaultsMissingUsersToOffline() {
		when(presenceRepository.findAllByUserIdIn(List.of(1L, 2L))).thenReturn(List.of(
			UserPresence.builder().userId(1L).status(PresenceStatus.ONLINE).build()
		));

		Map<Long, PresenceStatus> presence = presenceService.getPresence(List.of(1L, 2L));

		assertThat(presence).containsEntry(1L, PresenceStatus.ONLINE);
		assertThat(presence).containsEntry(2L, PresenceStatus.OFFLINE);
	}
}
