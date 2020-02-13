package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Bcl2FastqLaneEntry implements LimsKey {
  private final int laneNumber;
  private final String limsId;
  private final String limsProvider;
  private final ZonedDateTime limsTimestamp;
  private final String limsVersion;
  private final List<Bcl2FastqSampleEntry> samples;
  private final String signature;

  public Bcl2FastqLaneEntry(
      long laneNumber, LimsKey limsKey, List<Bcl2FastqSampleEntry> samples, String signature) {
    this.laneNumber = (int) laneNumber;
    limsId = limsKey.getId();
    limsProvider = limsKey.getProvider();
    limsVersion = limsKey.getVersion();
    limsTimestamp = limsKey.getLastModified();
    this.signature = signature;
    this.samples = samples;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Bcl2FastqLaneEntry entry = (Bcl2FastqLaneEntry) o;
    return laneNumber == entry.laneNumber
        && limsId.equals(entry.limsId)
        && limsProvider.equals(entry.limsProvider)
        && limsTimestamp.equals(entry.limsTimestamp)
        && limsVersion.equals(entry.limsVersion)
        && signature.equals(entry.signature)
        && samples.equals(entry.samples);
  }

  public void generateUUID(Consumer<byte[]> digest) {
    digest.accept(Utils.toBytes(laneNumber));
    digest.accept(limsId.getBytes(StandardCharsets.UTF_8));
    digest.accept(limsProvider.getBytes(StandardCharsets.UTF_8));
    digest.accept(Utils.toBytes(limsTimestamp.toEpochSecond()));
    digest.accept(limsVersion.getBytes(StandardCharsets.UTF_8));
    for (final Bcl2FastqSampleEntry sample : samples) {
      sample.generateUUID(digest);
    }
  }

  @Override
  public String getId() {
    return limsId;
  }

  @Override
  public ZonedDateTime getLastModified() {
    return limsTimestamp;
  }

  @Override
  public String getProvider() {
    return limsProvider;
  }

  @Override
  public String getVersion() {
    return limsVersion;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        laneNumber, limsId, limsProvider, limsTimestamp, limsVersion, signature, samples);
  }

  public Stream<LimsKey> limsKeys() {
    return Stream.concat(Stream.of(this), samples.stream());
  }

  public boolean matches(Pattern query) {
    return query.matcher(limsId).matches()
        || query.matcher(limsProvider).matches()
        || query.matcher(limsVersion).matches()
        || samples.stream().anyMatch(sample -> sample.matches(query));
  }

  public String prepare(ToIntFunction<LimsKey> createIusLimsKey) {
    return String.format("%d,%d:", laneNumber, createIusLimsKey.applyAsInt(this))
        + samples
            .stream()
            .map(sample -> sample.prepare(createIusLimsKey))
            .collect(Collectors.joining("+"));
  }

  public Stream<Pair<? extends LimsKey, String>> signatures() {
    return Stream.concat(
        Stream.of(new Pair<>(this, signature)),
        samples.stream().map(Bcl2FastqSampleEntry::signature));
  }
}
