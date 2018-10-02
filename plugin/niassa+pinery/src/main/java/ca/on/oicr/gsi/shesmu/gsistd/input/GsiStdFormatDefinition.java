package ca.on.oicr.gsi.shesmu.gsistd.input;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.util.input.BaseInputFormatDefinition;

@MetaInfServices(InputFormatDefinition.class)
public class GsiStdFormatDefinition extends BaseInputFormatDefinition<GsiStdValue, GsiStdRepository> {
	public GsiStdFormatDefinition() {
		super("gsi_std", GsiStdValue.class, GsiStdRepository.class);
	}
}
