package ca.on.oicr.gsi.shesmu.subsample;

import java.util.List;

public class Squish<T> extends Subsampler<T> {

	private final Subsampler<T> parent;
	private final long numberOfItems;

	public Squish(Subsampler<T> parent, long numberOfItems) {
		this.parent = parent;
		this.numberOfItems = numberOfItems;
	}

	@Override
	protected int subsample(List<T> input, List<T> output) {
		int position = parent.subsample(input, output);
		if (input.size() - position <= numberOfItems) {
			output.addAll(input.subList(position, input.size()));
			return input.size();
		}
		int step = (int) ((input.size() - position) / numberOfItems);
		int counter;
		for(counter = 0; position + counter * step < input.size() && counter < numberOfItems; counter++) {
			output.add(input.get(position + counter * step));
		}
		return position + counter;
		
	}

}
