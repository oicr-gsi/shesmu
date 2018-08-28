package ca.on.oicr.gsi.shesmu.input.nabu;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.BaseInputFormatDefinition;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;

@MetaInfServices(InputFormatDefinition.class)
public class NabuFormatDefinition extends BaseInputFormatDefinition<NabuValue, NabuRepository> {

	public NabuFormatDefinition() {
		super("nabu", NabuValue.class, NabuRepository.class);
	}

}
