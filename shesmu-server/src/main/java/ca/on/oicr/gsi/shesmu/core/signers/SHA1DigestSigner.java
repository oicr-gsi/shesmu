package ca.on.oicr.gsi.shesmu.core.signers;

import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.signature.DynamicSigner;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SHA1DigestSigner extends ca.on.oicr.gsi.shesmu.core.signers.ToBytesConverter
    implements DynamicSigner<String> {
  private final MessageDigest digest;

  public SHA1DigestSigner() throws NoSuchAlgorithmException {
    digest = MessageDigest.getInstance("SHA1");
  }

  @Override
  protected void add(byte[] bytes) {
    digest.update(bytes);
  }

  @Override
  public void addVariable(String name, Imyhat type, Object value) {
    digest.update(name.getBytes(StandardCharsets.UTF_8));
    digest.update((byte) ':');
    type.accept(this, value);
  }

  @Override
  public String finish() {
    return Utils.bytesToHex(digest.digest());
  }
}
