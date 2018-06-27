package ca.on.oicr.gsi.shesmu.subsample;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class Subsampler<T> {

	public Subsampler() {
	}
	
	public Stream<T> subsample(Stream<T> input) {
		List<T> output = new ArrayList<>();
		subsample(input.collect(Collectors.toList()), output);
		return output.stream();
	}
	
	protected abstract int subsample(List<T> input, List<T> output);
	
}
