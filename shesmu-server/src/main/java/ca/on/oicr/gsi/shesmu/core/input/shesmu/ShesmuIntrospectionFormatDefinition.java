package ca.on.oicr.gsi.shesmu.core.input.shesmu;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.util.input.BaseInputFormatDefinition;

@MetaInfServices(InputFormatDefinition.class)
public class ShesmuIntrospectionFormatDefinition
		extends BaseInputFormatDefinition<ShesmuIntrospectionValue, ShesmuIntrospectionRepository> {

	public ShesmuIntrospectionFormatDefinition() {
		super("shesmu", ShesmuIntrospectionValue.class, ShesmuIntrospectionRepository.class);
	}

}