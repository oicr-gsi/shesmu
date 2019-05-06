package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import java.time.Instant;
import java.time.ZoneId;
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

  public CellRangerIUSEntry(Tuple t) {
    final Tuple ius = (Tuple) t.get(0);
    ius_1 = ((Long) ius.get(1)).intValue();
    ius_2 = (String) ius.get(2);

    libraryName = (String) t.get(1);
    final Tuple lims = (Tuple) t.get(2);
    sampleId = (String) lims.get(0);
    version = (String) lims.get(1);
    provider = (String) lims.get(2);

    lastModified = ZonedDateTime.ofInstant((Instant) t.get(3), ZoneId.of("Z"));
    groupId = (String) t.get(4);
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
