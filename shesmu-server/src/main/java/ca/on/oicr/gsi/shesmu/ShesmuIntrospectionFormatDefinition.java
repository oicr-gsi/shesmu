package ca.on.oicr.gsi.shesmu;

import org.kohsuke.MetaInfServices;

@MetaInfServices(InputFormatDefinition.class)
public class ShesmuIntrospectionFormatDefinition
		extends BaseInputFormatDefinition<ShesmuIntrospectionValue, ShesmuIntrospectionRepository> {

	public ShesmuIntrospectionFormatDefinition() {
		super("shesmu", ShesmuIntrospectionValue.class, ShesmuIntrospectionRepository.class);
	}

}