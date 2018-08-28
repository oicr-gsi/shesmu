package ca.on.oicr.gsi.shesmu.input.shesmu;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.BaseInputFormatDefinition;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;

@MetaInfServices(InputFormatDefinition.class)
public class ShesmuIntrospectionFormatDefinition
		extends BaseInputFormatDefinition<ShesmuIntrospectionValue, ShesmuIntrospectionRepository> {

	public ShesmuIntrospectionFormatDefinition() {
		super("shesmu", ShesmuIntrospectionValue.class, ShesmuIntrospectionRepository.class);
	}

}