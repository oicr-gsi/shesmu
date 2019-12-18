package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class FilesInputFile implements LimsKey {
  private final ZonedDateTime lastModified;
  private final String provider;
  private final String sampleId;
  private final boolean stale;
  private final int swid;
  private final String version;

  public FilesInputFile(String swid, LimsKey limsKey, boolean stale) {
    this.swid = Integer.parseUnsignedInt(swid);

    sampleId = limsKey.getId();
    version = limsKey.getVersion();
    provider = limsKey.getProvider();
    lastModified = limsKey.getLastModified();

    this.stale = stale;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FilesInputFile that = (FilesInputFile) o;
    return stale == that.stale
        && swid == that.swid
        && lastModified.equals(that.lastModified)
        && provider.equals(that.provider)
        && sampleId.equals(that.sampleId)
        && version.equals(that.version);
  }

  public void generateUUID(Consumer<byte[]> digest) {
    digest.accept(new byte[] {(byte) (stale ? 's' : 'f')});
    digest.accept(Utils.toBytes(swid));
    digest.accept(Utils.toBytes(lastModified.toEpochSecond()));
    digest.accept(provider.getBytes(StandardCharsets.UTF_8));
    digest.accept(sampleId.getBytes(StandardCharsets.UTF_8));
    digest.accept(version.getBytes(StandardCharsets.UTF_8));
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
    return Objects.hash(lastModified, provider, sampleId, stale, swid, version);
  }

  public boolean isStale(Consumer<String> errorHandler) {
    if (stale) {
      errorHandler.accept(
          String.format(
              "Input file %d for %s is marked as stale. Fix provenance and purge this action.",
              swid, sampleId));
    }
    return stale;
  }

  public boolean matches(Pattern query) {
    return query.matcher(provider).matches()
        || query.matcher(sampleId).matches()
        || query.matcher(Integer.toString(swid)).matches()
        || query.matcher(version).matches();
  }

  public int swid() {
    return swid;
  }
}
