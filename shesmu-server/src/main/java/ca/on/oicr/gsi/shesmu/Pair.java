package ca.on.oicr.gsi.shesmu;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Immutable pair of two values
 */
public final class Pair<T, U> {
	public static <T, U> Consumer<Pair<T, U>> consume(BiConsumer<? super T, ? super U> consumer) {
		return pair -> pair.accept(consumer);
	}

	/**
	 * Create a stateful function that transforms an item in a pair with the first
	 * element being the index of this item.
	 *
	 * This is useful to number items using {@link Stream#map(Function)}
	 *
	 * @return
	 */
	public static <T> Function<T, Pair<Integer, T>> number() {
		return new Function<T, Pair<Integer, T>>() {
			int index = 0;

			@Override
			public Pair<Integer, T> apply(T t) {
				return new Pair<>(index++, t);
			}
		};
	}

	/**
	 * Create a predicate for pairs from a predicate that takes two inputs
	 */
	public static <T, U> Predicate<Pair<T, U>> predicate(BiPredicate<? super T, ? super U> predicate) {
		return pair -> pair.test(predicate);
	}

	/**
	 * Create a transformation function for pairs from a function that takes two
	 * inputs
	 */
	public static <T, U, R> Function<Pair<T, U>, R> transform(BiFunction<? super T, ? super U, ? extends R> function) {
		return pair -> pair.apply(function);
	}

	private final T first;

	private final U second;

	public Pair(T first, U second) {
		super();
		this.first = first;
		this.second = second;
	}

	/**
	 * Consume both values from this pair
	 */
	public void accept(BiConsumer<? super T, ? super U> consumer) {
		consumer.accept(first, second);
	}

	/**
	 * Transform this pair using a function
	 */
	public <R> R apply(BiFunction<? super T, ? super U, R> function) {
		return function.apply(first, second);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		@SuppressWarnings("rawtypes")
		final Pair other = (Pair) obj;
		if (first == null) {
			if (other.first != null) {
				return false;
			}
		} else if (!first.equals(other.first)) {
			return false;
		}
		if (second == null) {
			if (other.second != null) {
				return false;
			}
		} else if (!second.equals(other.second)) {
			return false;
		}
		return true;
	}

	/**
	 * Get the first item in this pair
	 */
	public T first() {
		return first;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (first == null ? 0 : first.hashCode());
		result = prime * result + (second == null ? 0 : second.hashCode());
		return result;
	}

	/**
	 * Transform this pair to a new pair by transforming both elements independently
	 */
	public <V, W> Pair<V, W> map(Function<? super T, ? extends V> firstFunction,
			Function<? super U, ? extends W> secondFunction) {
		return new Pair<>(firstFunction.apply(first), secondFunction.apply(second));
	}

	/**
	 * Get the second item in this pair
	 *
	 * @return
	 */
	public U second() {
		return second;
	}

	/**
	 * Check that this pair meets some condition
	 */
	public boolean test(BiPredicate<? super T, ? super U> predicate) {
		return predicate.test(first, second);
	}

	@Override
	public String toString() {
		return "<" + first + ", " + second + ">";
	}

}
