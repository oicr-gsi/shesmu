package ca.on.oicr.gsi.shesmu;

import org.kohsuke.MetaInfServices;

@MetaInfServices(InputFormatDefinition.class)
public class GsiStdFormatDefinition extends BaseInputFormatDefinition<GsiStdValue, GsiStdRepository> {
	public GsiStdFormatDefinition() {
		super("gsi_std", GsiStdValue.class, GsiStdRepository.class);
	}
}
