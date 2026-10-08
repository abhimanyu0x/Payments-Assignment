package dev.dodo.receiver;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReceivedEvents {
	private final ReceivedEventRepository events;

	@Transactional
	public boolean saveIfNew(ReceivedEventEntity event) {
		if (events.existsById(event.getEventId())) return false;
		events.saveAndFlush(event);
		return true;
	}

	@Transactional(readOnly = true)
	public List<String> recentPayloads() {
		return events.findTop100ByOrderByReceivedAtDesc().stream().map(ReceivedEventEntity::getPayload).toList();
	}
}
