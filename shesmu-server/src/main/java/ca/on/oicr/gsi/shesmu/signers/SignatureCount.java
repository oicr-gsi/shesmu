package ca.on.oicr.gsi.shesmu.signers;

import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.SignatureStorage;
import ca.on.oicr.gsi.shesmu.SignatureVariable;
import ca.on.oicr.gsi.shesmu.compiler.Target;

@MetaInfServices
public final class SignatureCount extends SignatureVariable {

	public SignatureCount() {
		super("signature_count", SignatureStorage.STATIC_FIELD, Imyhat.INTEGER);
	}

	@Override
	public void build(GeneratorAdapter method, Type initialType, Stream<Target> variables) {
		method.push(variables.count());
	}
}
