package ca.on.oicr.gsi.shesmu.input.gsistd;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.BaseInputFormatDefinition;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;

@MetaInfServices(InputFormatDefinition.class)
public class GsiStdFormatDefinition extends BaseInputFormatDefinition<GsiStdValue, GsiStdRepository> {
	public GsiStdFormatDefinition() {
		super("gsi_std", GsiStdValue.class, GsiStdRepository.class);
	}
}
