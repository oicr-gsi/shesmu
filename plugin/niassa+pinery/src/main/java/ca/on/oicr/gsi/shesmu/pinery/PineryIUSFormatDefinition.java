package ca.on.oicr.gsi.shesmu.pinery;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.util.input.BaseInputFormatDefinition;

@MetaInfServices(InputFormatDefinition.class)
public class PineryIUSFormatDefinition extends BaseInputFormatDefinition<PineryIUSValue, PineryIUSRepository> {
	public PineryIUSFormatDefinition() {
		super("pinery_ius", PineryIUSValue.class, PineryIUSRepository.class);
	}
}
