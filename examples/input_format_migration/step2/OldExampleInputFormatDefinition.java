package ca.on.oicr.gsi.shesmu.example;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.util.input.DecoratedInputFormatDefinition;

@MetaInfServices(InputFormatDefinition.class)
public class OldExampleInputFormatDefinition extends DecoratedInputFormatDefinition<ExampleValue, OldExampleValue> {

	public OldExampleInputFormatDefinition() {
		super("example_v1", ExampleValue.class, OldExampleValue.class);
	}

	@Override
	protected OldExampleValue wrap(ExampleValue input) {
		return new OldExampleValue(input);
	}

}
