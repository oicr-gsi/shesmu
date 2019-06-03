package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.provenance.model.LimsKey;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

final class CellRangerIUSEntry implements LimsKey {
  private final String groupId;
  private final int ius_1;
  private final String ius_2;
  private final ZonedDateTime lastModified;
  private final String libraryName;
  private final String provider;
  private final String sampleId;
  private final String version;

  public CellRangerIUSEntry(IusTriple ius, String libraryName, LimsKey limsKey, String groupId) {
    ius_1 = ius.lane();
    ius_2 = ius.barcode();

    this.libraryName = libraryName;

    sampleId = limsKey.getId();
    version = limsKey.getVersion();
    provider = limsKey.getProvider();
    lastModified = limsKey.getLastModified();

    this.groupId = groupId;
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
    CellRangerIUSEntry limsKey = (CellRangerIUSEntry) o;
    return ius_1 == limsKey.ius_1
        && groupId.equals(limsKey.groupId)
        && ius_2.equals(limsKey.ius_2)
        && lastModified.equals(limsKey.lastModified)
        && libraryName.equals(limsKey.libraryName)
        && provider.equals(limsKey.provider)
        && sampleId.equals(limsKey.sampleId)
        && version.equals(limsKey.version);
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
        groupId, ius_1, ius_2, lastModified, libraryName, provider, sampleId, version);
  }

  public boolean matches(Pattern query) {
    return query.matcher(groupId).matches()
        || query.matcher(ius_2).matches()
        || query.matcher(libraryName).matches()
        || query.matcher(provider).matches()
        || query.matcher(sampleId).matches();
  }
}
