package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

final class CellRangerLimsKey implements StringableLimsKey {
  private final String groupId;
  private final int ius_1;
  private final String ius_2;
  private final ZonedDateTime lastModified;
  private final String libraryName;
  private final String provider;
  private final String sampleId;
  private final String version;

  public CellRangerLimsKey(Tuple t) {
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

  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final CellRangerLimsKey other = (CellRangerLimsKey) obj;
    if (groupId == null) {
      if (other.groupId != null) {
        return false;
      }
    } else if (!groupId.equals(other.groupId)) {
      return false;
    }
    if (ius_1 != other.ius_1) {
      return false;
    }
    if (ius_2 == null) {
      if (other.ius_2 != null) {
        return false;
      }
    } else if (!ius_2.equals(other.ius_2)) {
      return false;
    }
    if (libraryName == null) {
      if (other.libraryName != null) {
        return false;
      }
    } else if (!libraryName.equals(other.libraryName)) {
      return false;
    }
    if (lastModified == null) {
      if (other.lastModified != null) {
        return false;
      }
    } else if (!lastModified.toInstant().equals(other.lastModified.toInstant())) {
      return false;
    }
    if (provider == null) {
      if (other.provider != null) {
        return false;
      }
    } else if (!provider.equals(other.provider)) {
      return false;
    }
    if (sampleId == null) {
      if (other.sampleId != null) {
        return false;
      }
    } else if (!sampleId.equals(other.sampleId)) {
      return false;
    }
    if (version == null) {
      if (other.version != null) {
        return false;
      }
    } else if (!version.equals(other.version)) {
      return false;
    }
    return true;
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
    final int prime = 31;
    int result = 1;
    result = prime * result + (groupId == null ? 0 : groupId.hashCode());
    result = prime * result + ius_1;
    result = prime * result + (ius_2 == null ? 0 : ius_2.hashCode());
    result = prime * result + (libraryName == null ? 0 : libraryName.hashCode());
    result = prime * result + (lastModified == null ? 0 : lastModified.toInstant().hashCode());
    result = prime * result + (provider == null ? 0 : provider.hashCode());
    result = prime * result + (sampleId == null ? 0 : sampleId.hashCode());
    result = prime * result + (version == null ? 0 : version.hashCode());
    return result;
  }

  @Override
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
}
