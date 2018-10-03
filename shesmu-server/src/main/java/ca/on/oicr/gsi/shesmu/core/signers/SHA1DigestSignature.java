package ca.on.oicr.gsi.shesmu.core.signers;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.SignatureVariable;

@MetaInfServices(SignatureVariable.class)
public final class SHA1DigestSignature extends SignatureVariableForSigner<SHA1DigestSigner, String> {

	public SHA1DigestSignature() {
		super("sha1_signature", SHA1DigestSigner.class, Imyhat.STRING, String.class);
	}

}
