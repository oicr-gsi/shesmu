package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.Pair;
import java.util.List;
import java.util.stream.Stream;

/** Rules for combining prepared filters with ones discovered from another source (e.g., JIRA) */
public enum JoiningRule {
  /** Take the base filter and remove all the accessory filters */
  ALL_EXCEPT {
    @Override
    public <F> Stream<Pair<String, F>> join(
        String baseName,
        List<F> baseFilters,
        Stream<Pair<String, F>> accessoryFilters,
        FilterBuilder<F> builder) {
      return Stream.of(
          new Pair<>(
              baseName,
              builder.and(
                  Stream.concat(
                      baseFilters.stream(),
                      accessoryFilters.map(Pair::second).map(builder::negate)))));
    }
  },
  /** Take the base filter and intersect it with the union of all accessory filters */
  ALL_AND {
    @Override
    public <F> Stream<Pair<String, F>> join(
        String baseName,
        List<F> baseFilters,
        Stream<Pair<String, F>> accessoryFilters,
        FilterBuilder<F> builder) {
      return Stream.of(
          new Pair<>(
              baseName,
              builder.and(
                  Stream.concat(
                      baseFilters.stream(),
                      Stream.of(builder.or(accessoryFilters.map(Pair::second)))))));
    }
  },
  /**
   * Take each accessory filter and produce the intersection of the base filter and the accessory
   * filter
   */
  EACH_AND {
    @Override
    public <F> Stream<Pair<String, F>> join(
        String baseName,
        List<F> baseFilters,
        Stream<Pair<String, F>> accessoryFilters,
        FilterBuilder<F> builder) {
      return accessoryFilters.map(
          p ->
              new Pair<>(
                  p.first(),
                  builder.and(Stream.concat(Stream.of(p.second()), baseFilters.stream()))));
    }
  };

  /**
   * Create a list of filters based on the desired joining strategy
   *
   * @param baseName the output name if only one filter is produced
   * @param baseFilters the filters that should be fixed across all the output
   * @param accessoryFilters the set of filters that are provided from a dynamic location; each one
   *     has an individual label
   * @param builder the filter builder
   * @param <F> the type of filter output
   * @return a stream of output; this may contain one item or many depending on the joining rule
   */
  public abstract <F> Stream<Pair<String, F>> join(
      String baseName,
      List<F> baseFilters,
      Stream<Pair<String, F>> accessoryFilters,
      FilterBuilder<F> builder);
}
