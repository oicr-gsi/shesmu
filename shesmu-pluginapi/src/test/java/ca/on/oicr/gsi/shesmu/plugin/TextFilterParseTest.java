package ca.on.oicr.gsi.shesmu.plugin;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilter;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilterBuilder;
import ca.on.oicr.gsi.shesmu.plugin.filter.SourceOliveLocation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TextFilterParseTest {
  private abstract static class TestFilterBuilder implements ActionFilterBuilder<Boolean> {
    @Override
    public Boolean added(Optional<Instant> start, Optional<Instant> end) {
      return false;
    }

    @Override
    public Boolean tag(Pattern pattern) {
      return false;
    }

    @Override
    public Boolean and(Stream<Boolean> filters) {
      return filters.allMatch(x -> x);
    }

    @Override
    public Boolean checked(Optional<Instant> start, Optional<Instant> end) {
      return false;
    }

    @Override
    public Boolean external(Optional<Instant> start, Optional<Instant> end) {
      return false;
    }

    @Override
    public Boolean fromFile(String... files) {
      return false;
    }

    @Override
    public Boolean fromSourceLocation(Stream<SourceOliveLocation> locations) {
      return false;
    }

    @Override
    public Boolean isState(ActionState... states) {
      return false;
    }

    @Override
    public Boolean negate(Boolean filter) {
      return filter;
    }

    @Override
    public Boolean or(Stream<Boolean> filters) {
      return filters.allMatch(x -> x);
    }

    @Override
    public Boolean statusChanged(Optional<Instant> start, Optional<Instant> end) {
      return false;
    }

    @Override
    public Boolean tags(Stream<String> tags) {
      return false;
    }

    @Override
    public Boolean type(String... types) {
      return false;
    }
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  public void testGoodBoth() {
    Assertions.assertTrue(
        ActionFilter.extractFromText(
                "Random stuff \nshesmu:799AEF722968FF2D5433515989dc565e5ab54AAA shesmusearch:W3sidHlwZSI6InRleHQiLCJtYXRjaENhc2UiOmZhbHNlLCJ0ZXh0IjoidGVzdCJ9XQ==  ",
                MAPPER)
            .map(
                filter ->
                    filter.convert(
                        new TestFilterBuilder() {
                          @Override
                          public Boolean ids(List<String> ids) {
                            return ids.size() == 1
                                && ids.get(0)
                                    .equals("shesmu:799AEF722968FF2D5433515989DC565E5AB54AAA");
                          }

                          @Override
                          public Boolean textSearch(Pattern pattern) {
                            return pattern.matcher("test").matches();
                          }
                        }))
            .orElse(false));
  }

  @Test
  public void testGoodID() {
    Assertions.assertTrue(
        ActionFilter.extractFromText(
                "Hot garbage shesmu:799AEF722968FF2D5433515989DC565E5AB54AAA  ", MAPPER)
            .map(
                filter ->
                    filter.convert(
                        new TestFilterBuilder() {
                          @Override
                          public Boolean ids(List<String> ids) {
                            return ids.size() == 1
                                && ids.get(0)
                                    .equals("shesmu:799AEF722968FF2D5433515989DC565E5AB54AAA");
                          }

                          @Override
                          public Boolean textSearch(Pattern pattern) {
                            return false;
                          }
                        }))
            .orElse(false));
  }

  @Test
  public void testGoodQuery() {
    Assertions.assertTrue(
        ActionFilter.extractFromText(
                "So much garbage shesmusearch:W3sidHlwZSI6InRleHQiLCJtYXRjaENhc2UiOmZhbHNlLCJ0ZXh0IjoidGVzdCJ9XQ==  ",
                MAPPER)
            .map(
                filter ->
                    filter.convert(
                        new TestFilterBuilder() {
                          @Override
                          public Boolean ids(List<String> ids) {
                            return false;
                          }

                          @Override
                          public Boolean textSearch(Pattern pattern) {
                            return pattern.matcher("test").matches();
                          }
                        }))
            .orElse(false));
  }

  @Test
  public void testNothing() {
    Assertions.assertFalse(
        ActionFilter.extractFromText("NOTHING OF VALUE!!! SADNESS!!! DESPAIR!!!", MAPPER)
            .isPresent());
  }
}
