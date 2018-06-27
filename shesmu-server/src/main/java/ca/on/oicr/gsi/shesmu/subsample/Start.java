package ca.on.oicr.gsi.shesmu.subsample;

import java.util.List;

public class Start<T> extends Subsampler<T>{

	public Start() {
	}

	@Override
	protected int subsample(List<T> input, List<T> output) {
		return 0;
	}

}
