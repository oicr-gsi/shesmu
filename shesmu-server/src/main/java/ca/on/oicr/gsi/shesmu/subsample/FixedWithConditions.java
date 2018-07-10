package ca.on.oicr.gsi.shesmu.subsample;

import java.util.List;
import java.util.function.Predicate;

public class FixedWithConditions<T> extends Subsampler<T> {

	private final Predicate<T> condition;
	private final long numberOfItems;
	private final Subsampler<T> parent;

	public FixedWithConditions(Subsampler<T> parent, long numberOfItems, Predicate<T> condition) {
		this.parent = parent;
		this.numberOfItems = numberOfItems;
		this.condition = condition;

	}

	@Override
	protected int subsample(List<T> input, List<T> output) {
		final int position = parent.subsample(input, output);
		int counter;
		for (counter = 0; position + counter < input.size() && counter < numberOfItems; counter++) {
			final T item = input.get(position + counter);
			if (!condition.test(item)) {
				break;
			}
			output.add(item);
		}
		return position + counter;
	}

}
