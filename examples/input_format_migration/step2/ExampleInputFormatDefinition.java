package ca.on.oicr.gsi.shesmu.example;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.util.input.BaseInputFormatDefinition;

@MetaInfServices(InputFormatDefinition.class)
public class ExampleInputFormatDefinition extends BaseInputFormatDefinition<ExampleValue, ExampleRepository> {

	public ExampleInputFormatDefinition() {
		super("example_v2", ExampleValue.class, ExampleRepository.class);
	}
}
