package ca.on.oicr.gsi.shesmu.nabu;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.util.input.BaseInputFormatDefinition;

@MetaInfServices(InputFormatDefinition.class)
public class NabuFormatDefinition extends BaseInputFormatDefinition<NabuValue, NabuRepository> {

	public NabuFormatDefinition() {
		super("nabu", NabuValue.class, NabuRepository.class);
	}

}
