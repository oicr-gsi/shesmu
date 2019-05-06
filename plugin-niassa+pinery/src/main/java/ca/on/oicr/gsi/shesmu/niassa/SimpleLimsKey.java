package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.provenance.model.LimsKey;
import java.time.ZonedDateTime;
import java.util.Objects;

public class SimpleLimsKey implements LimsKey {
  private final String id;
  private final ZonedDateTime lastModified;
  private final String provider;
  private final String version;

  public SimpleLimsKey(LimsKey original) {
    provider = original.getProvider();
    id = original.getId();
    version = original.getVersion();
    lastModified = original.getLastModified();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SimpleLimsKey that = (SimpleLimsKey) o;
    return provider.equals(that.provider) && id.equals(that.id) && version.equals(that.version);
  }

  @Override
  public String getId() {
    return id;
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
    return Objects.hash(provider, id, version);
  }

  @Override
  public String toString() {
    return String.format("{%s %s %s}", provider, id, version);
  }
}
