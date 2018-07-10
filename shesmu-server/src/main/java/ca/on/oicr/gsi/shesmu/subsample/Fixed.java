package ca.on.oicr.gsi.shesmu.subsample;

import java.util.List;

public class Fixed<T> extends Subsampler<T> {

	private final long numberOfItems;
	private final Subsampler<T> parent;

	public Fixed(Subsampler<T> parent, long numberOfItems) {
		this.parent = parent;
		this.numberOfItems = numberOfItems;

	}

	@Override
	protected int subsample(List<T> input, List<T> output) {
		final int position = parent.subsample(input, output);
		int counter;
		for (counter = 0; position + counter < input.size() && counter < numberOfItems; counter++) {
			output.add(input.get(position + counter));
		}
		return position + counter;
	}

}
