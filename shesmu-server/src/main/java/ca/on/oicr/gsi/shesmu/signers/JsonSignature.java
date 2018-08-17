package ca.on.oicr.gsi.shesmu.signers;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.SignatureVariable;

@MetaInfServices(SignatureVariable.class)
public final class JsonSignature extends SignatureVariableForSigner<JsonSigner, String> {

	public JsonSignature() {
		super("json_signature", JsonSigner.class, Imyhat.STRING, String.class);
	}

}
