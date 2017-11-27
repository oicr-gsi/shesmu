package ca.on.oicr.gsi.shesmu;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Immutable pair of two values
 */
public final class Pair<T, U> {
	public static <T> Function<T, Pair<Integer, T>> number() {
		return new Function<T, Pair<Integer, T>>() {
			int index = 0;

			@Override
			public Pair<Integer, T> apply(T t) {
				return new Pair<>(index++, t);
			}
		};
	}

	public static <T, U> Predicate<Pair<T, U>> predicate(BiPredicate<T, U> predicate) {
		return pair -> pair.test(predicate);
	}

	public static <T, U, R> Function<Pair<T, U>, R> transform(BiFunction<T, U, R> function) {
		return pair -> pair.apply(function);
	}

	private final T first;

	private final U second;

	public Pair(T first, U second) {
		super();
		this.first = first;
		this.second = second;
	}

	public void accept(BiConsumer<T, U> consumer) {
		consumer.accept(first, second);
	}

	public <R> R apply(BiFunction<T, U, R> function) {
		return function.apply(first, second);
	}

	public T first() {
		return first;
	}

	public <V, W> Pair<V, W> map(Function<? super T, V> firstFunction, Function<? super U, W> secondFunction) {
		return new Pair<>(firstFunction.apply(first), secondFunction.apply(second));
	}

	public U second() {
		return second;
	}

	public boolean test(BiPredicate<T, U> predicate) {
		return predicate.test(first, second);
	}
}
