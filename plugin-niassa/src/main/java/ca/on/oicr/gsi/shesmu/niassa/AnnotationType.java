package ca.on.oicr.gsi.shesmu.niassa;

import net.sourceforge.seqware.common.metadata.Metadata;
import net.sourceforge.seqware.common.model.Annotatable;
import net.sourceforge.seqware.common.model.Attribute;

public interface AnnotationType<A extends Attribute<?, A>> {

  A create();

  Annotatable<A> fetch(Metadata metadata, int accession);

  String name();

  void save(Metadata metadata, int accession, A attribute);
}
