package dev.dodo.common;

import com.querydsl.core.types.Predicate;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class Pages {
	private static final String CREATED_AT = "createdAt";
	private static final String ID = "id";
	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, CREATED_AT, ID);

	@Transactional(readOnly = true)
	public <E, T> Page<T> list(QuerydslPredicateExecutor<E> repository, Predicate where, UUID business, String list, PageQuery page, Function<E, T> toItem) {
		String scope = business + "|" + list;
		var position = StringUtils.hasText(page.cursor()) ? decode(page.cursor(), scope) : ScrollPosition.keyset();
		var window = repository.findBy(where, query -> query.sortBy(NEWEST_FIRST).limit(page.limit()).scroll(position));
		String next = window.hasNext() ? encode(scope, (KeysetScrollPosition) window.positionAt(window.size() - 1)) : null;
		return new Page<>(window.map(toItem).getContent(), next);
	}

	private static String encode(String scope, KeysetScrollPosition last) {
		String raw = scope + "|" + last.getKeys().get(CREATED_AT) + "|" + last.getKeys().get(ID);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	private static ScrollPosition decode(String cursor, String scope) {
		try {
			String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			if (!raw.startsWith(scope + "|")) throw new IllegalArgumentException();
			var parts = raw.substring(scope.length() + 1).split("\\|", -1);
			var keys = new LinkedHashMap<String, Object>();
			keys.put(CREATED_AT, Instant.parse(parts[0]));
			keys.put(ID, UUID.fromString(parts[1]));
			return ScrollPosition.forward(keys);
		} catch (RuntimeException e) {
			throw new ApiError(400, "invalid_cursor", Messages.PAGE_LINK_EXPIRED);
		}
	}
}
