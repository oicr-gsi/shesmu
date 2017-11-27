package ca.on.oicr.gsi.shesmu;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Packs named objects into a map for retrieval
 *
 * @param <T>
 *            the values in the map
 */
public final class NameLoader<T> {

	private final Map<String, T> items;

	public NameLoader(Stream<T> data, Function<T, String> getName) {
		items = data.collect(Collectors.toMap(getName, Function.identity()));
	}

	public Stream<T> all() {
		return items.values().stream();
	}

	public T get(String name) {
		return items.get(name);
	}
}
