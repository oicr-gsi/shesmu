package ca.on.oicr.gsi.shesmu.core.input.shesmu;

import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.util.input.BaseInputFormatDefinition;
import org.kohsuke.MetaInfServices;

@MetaInfServices(InputFormatDefinition.class)
public class ShesmuIntrospectionFormatDefinition
    extends BaseInputFormatDefinition<ShesmuIntrospectionValue, ShesmuIntrospectionRepository> {

  public ShesmuIntrospectionFormatDefinition() {
    super("shesmu", ShesmuIntrospectionValue.class, ShesmuIntrospectionRepository.class);
  }
}
