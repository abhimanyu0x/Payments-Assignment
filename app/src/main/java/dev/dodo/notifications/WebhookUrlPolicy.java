package dev.dodo.notifications;

import dev.dodo.configuration.AppProperties;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class WebhookUrlPolicy {
	public enum Verdict {
		ALLOWED,
		BLOCKED,
		UNRESOLVED
	}

	private static final byte[] NAT64_PREFIX = {0, 0x64, (byte) 0xff, (byte) 0x9b, 0, 0, 0, 0, 0, 0, 0, 0};
	private final AppProperties app;

	public Verdict check(String url) {
		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException e) {
			return Verdict.BLOCKED;
		}
		String scheme = Objects.requireNonNullElse(uri.getScheme(), "").toLowerCase(Locale.ROOT);
		String host = uri.getHost();
		if (!StringUtils.hasText(host) || Objects.nonNull(uri.getRawUserInfo()) || Objects.nonNull(uri.getRawFragment())) return Verdict.BLOCKED;
		if (trusted(host)) return scheme.equals("https") || scheme.equals("http") ? Verdict.ALLOWED : Verdict.BLOCKED;
		if (!scheme.equals("https")) return Verdict.BLOCKED;
		try {
			var addresses = InetAddress.getAllByName(host);
			return addresses.length > 0 && Arrays.stream(addresses).allMatch(WebhookUrlPolicy::isPublic) ? Verdict.ALLOWED : Verdict.BLOCKED;
		} catch (UnknownHostException e) {
			return Verdict.UNRESOLVED;
		}
	}

	static String normalize(String url) {
		var uri = URI.create(url);
		return UriComponentsBuilder.fromUri(uri).scheme(uri.getScheme().toLowerCase(Locale.ROOT)).host(uri.getHost().toLowerCase(Locale.ROOT)).build(true).toUriString();
	}

	private boolean trusted(String host) {
		return app.webhookTrustedHosts().stream().map(String::strip).anyMatch(host::equalsIgnoreCase);
	}

	private static boolean isPublic(InetAddress address) {
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
		byte[] raw = address.getAddress();
		if (address instanceof Inet6Address) {
			if (Arrays.equals(raw, 0, NAT64_PREFIX.length, NAT64_PREFIX, 0, NAT64_PREFIX.length)) return isPublicIpv4(Arrays.copyOfRange(raw, 12, 16));
			return (raw[0] & 0xFE) != 0xFC;
		}
		return isPublicIpv4(raw);
	}

	private static boolean isPublicIpv4(byte[] raw) {
		int first = raw[0] & 0xFF, second = raw[1] & 0xFF;
		if (first == 0 || first == 10 || first == 127 || first >= 224) return false;
		if (first == 169 && second == 254 || first == 172 && second >= 16 && second <= 31 || first == 192 && second == 168) return false;
		return !(first == 100 && second >= 64 && second <= 127) && !(first == 192 && second == 0) && !(first == 198 && (second == 18 || second == 19));
	}
}
