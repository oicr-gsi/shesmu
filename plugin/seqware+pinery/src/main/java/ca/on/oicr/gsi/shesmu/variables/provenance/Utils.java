package ca.on.oicr.gsi.shesmu.variables.provenance;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import ca.on.oicr.gsi.provenance.ProviderLoader;

public final class Utils {
	private static final Pattern LANE_NUMBER = Pattern.compile("^.*_(\\d+)$");

	public static final Optional<ProviderLoader> LOADER = Optional.ofNullable(System.getenv("PROVENANCE_SETTINGS"))//
			.map(Paths::get)//
			.flatMap(path -> {
				try {
					return Optional.of(new ProviderLoader(new String(Files.readAllBytes(path))));

				} catch (final Exception e) {
					e.printStackTrace();
					return Optional.empty();
				}
			});

	public static long parseLaneNumber(String laneName) {
		try {
			return Long.parseUnsignedLong(laneName);
		} catch (final NumberFormatException e) {
			// Try something else.
		}
		final Matcher laneMatcher = LANE_NUMBER.matcher(laneName);
		if (laneMatcher.matches()) {
			return parseLong(laneMatcher.group(1));
		}
		return 0;
	}

	public static long parseLong(String input) {
		try {
			return Long.parseLong(input);
		} catch (final NumberFormatException e) {
			return 0;
		}
	}

	static <T> void setProvider(Map<String, T> source, BiConsumer<String, T> consumer) {
		source.entrySet().stream().forEach(entry -> consumer.accept(entry.getKey(), entry.getValue()));
	}

	public static <T> Optional<T> singleton(Collection<T> items, Consumer<String> isBad, boolean required) {
		if (items == null) {
			if (required) {
				isBad.accept("null");
			}
			return Optional.empty();
		}
		switch (items.size()) {
		case 0:
			if (required) {
				isBad.accept("empty");
			}
			return Optional.empty();
		case 1:
			return Optional.of(items.iterator().next());
		default:
			isBad.accept("multiple");
			return Optional.of(items.iterator().next());
		}
	}

	public static <T> Stream<T> stream(Collection<T> collection) {
		return collection == null ? Stream.empty() : collection.stream();
	}

	private Utils() {
	}
}
