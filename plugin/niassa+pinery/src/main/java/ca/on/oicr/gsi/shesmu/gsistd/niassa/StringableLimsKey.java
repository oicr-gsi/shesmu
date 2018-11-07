package ca.on.oicr.gsi.shesmu.gsistd.niassa;

import ca.on.oicr.gsi.provenance.model.LimsKey;

public interface StringableLimsKey extends LimsKey {

	String asLaneString(int swid);
}
