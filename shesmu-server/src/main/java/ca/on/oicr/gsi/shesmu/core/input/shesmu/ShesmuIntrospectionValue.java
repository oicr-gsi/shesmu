package ca.on.oicr.gsi.shesmu.core.input.shesmu;

import ca.on.oicr.gsi.shesmu.plugin.SourceLocation;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class ShesmuIntrospectionValue {
  private final Action action;
  private final Instant changed;
  private final Instant checked;
  private final Instant generated;
  private final String id;
  private JsonNode json;
  private final Instant lastStateTransition;
  private final Set<Tuple> locations;
  private final ActionState state;
  private final Set<String> tags;

  public ShesmuIntrospectionValue(
      Action action,
      String id,
      Instant changed,
      Instant checked,
      Instant generated,
      Instant lastStateTransition,
      ActionState state,
      Set<SourceLocation> locations,
      Set<String> tags) {
    super();
    this.action = action;
    this.id = id;
    this.changed = changed;
    this.checked = checked;
    this.generated = generated;
    this.lastStateTransition = lastStateTransition;
    this.state = state;
    this.locations =
        locations.stream()
            .map(
                l ->
                    new Tuple(
                        Long.valueOf(l.column()),
                        Paths.get(l.fileName()),
                        l.hash(),
                        Long.valueOf(l.line())))
            .collect(Collectors.toSet());
    this.tags = tags;
  }

  @ShesmuVariable
  public Instant changed() {
    return changed;
  }

  @ShesmuVariable
  public Instant checked() {
    return checked;
  }

  @ShesmuVariable
  public Optional<Instant> external_timestamp() {
    return action.externalTimestamp();
  }

  @ShesmuVariable
  public Instant generated() {
    return generated;
  }

  @ShesmuVariable
  public String id() {
    return id;
  }

  @ShesmuVariable
  public JsonNode info() {
    if (json == null) {
      json = action.toJson(RuntimeSupport.MAPPER);
    }
    return json;
  }

  @ShesmuVariable
  public Instant last_state_transition() {
    return lastStateTransition;
  }

  @ShesmuVariable(type = "ao4column$ifile$phash$sline$i")
  public Set<Tuple> locations() {
    return locations;
  }

  @ShesmuVariable
  public long priority() {
    return action.priority();
  }

  @ShesmuVariable
  public long retry() {
    return action.retryMinutes();
  }

  @ShesmuVariable
  public String state() {
    return state.name();
  }

  @ShesmuVariable
  public Set<String> tags() {
    return tags;
  }

  @ShesmuVariable
  public String type() {
    return action.type();
  }
}
