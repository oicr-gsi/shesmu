package ca.on.oicr.gsi.shesmu.core.signers;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.SignatureVariable;
import org.kohsuke.MetaInfServices;

@MetaInfServices(SignatureVariable.class)
public final class JsonSignature extends SignatureVariableForSigner<JsonSigner, String> {

  public JsonSignature() {
    super("json_signature", JsonSigner.class, Imyhat.STRING, String.class);
  }
}
