package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

final class SignedCellRangerIUSEntry implements LimsKey {
  private final String groupId;
  private final int ius_1;
  private final String ius_2;
  private final ZonedDateTime lastModified;
  private final String libraryName;
  private final String provider;
  private final String sampleId;
  private final String signature;
  private final String version;

  public SignedCellRangerIUSEntry(
      String groupId, IusTriple ius, String libraryName, LimsKey limsKey, String signature) {
    ius_1 = ius.lane();
    ius_2 = ius.barcode();

    this.libraryName = libraryName;

    sampleId = limsKey.getId();
    version = limsKey.getVersion();
    provider = limsKey.getProvider();
    lastModified = limsKey.getLastModified();

    this.groupId = groupId;
    this.signature = signature;
  }

  public String asLaneString(int swid) {
    return String.join(
        ",", //
        Integer.toString(ius_1), // lane
        ius_2, // barcode
        Integer.toString(swid), // IUS SWID
        libraryName, // library/sample name
        groupId // group ID
        );
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SignedCellRangerIUSEntry limsKey = (SignedCellRangerIUSEntry) o;
    return ius_1 == limsKey.ius_1
        && groupId.equals(limsKey.groupId)
        && ius_2.equals(limsKey.ius_2)
        && lastModified.equals(limsKey.lastModified)
        && libraryName.equals(limsKey.libraryName)
        && provider.equals(limsKey.provider)
        && sampleId.equals(limsKey.sampleId)
        && signature.equals(limsKey.signature)
        && version.equals(limsKey.version);
  }

  public void generateUUID(Consumer<byte[]> digest) {
    digest.accept(Utils.toBytes(ius_1));
    digest.accept(groupId.getBytes(StandardCharsets.UTF_8));
    digest.accept(ius_2.getBytes(StandardCharsets.UTF_8));
    digest.accept(Utils.toBytes(lastModified.toEpochSecond()));
    digest.accept(libraryName.getBytes(StandardCharsets.UTF_8));
    digest.accept(provider.getBytes(StandardCharsets.UTF_8));
    digest.accept(sampleId.getBytes(StandardCharsets.UTF_8));
    digest.accept(version.getBytes(StandardCharsets.UTF_8));
    digest.accept(signature.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public String getId() {
    return sampleId;
  }

  @Override
  public ZonedDateTime getLastModified() {
    return lastModified;
  }

  @Override
  public String getProvider() {
    return provider;
  }

  @Override
  public String getVersion() {
    return version;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        groupId, ius_1, ius_2, lastModified, libraryName, provider, sampleId, signature, version);
  }

  public boolean matches(Pattern query) {
    return query.matcher(groupId).matches()
        || query.matcher(ius_2).matches()
        || query.matcher(libraryName).matches()
        || query.matcher(provider).matches()
        || query.matcher(sampleId).matches()
        || query.matcher(signature).matches();
  }

  public Pair<? extends LimsKey, String> signature() {
    return new Pair<>(this, signature);
  }
}
