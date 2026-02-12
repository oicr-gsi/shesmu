package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.vidarr.api.VidarrRequest;
import java.util.stream.Stream;

public abstract class VidarrAction extends Action {
  VidarrRequest request;
  static final Imyhat EXTERNAL_IDS =
      new Imyhat.ObjectImyhat(
              Stream.of(new Pair<>("id", Imyhat.STRING), new Pair<>("provider", Imyhat.STRING)))
          .asList();
  /**
   * Construct a new action instance
   *
   * @param type the type of the action for use by the front-end
   */
  public VidarrAction(String type) {
    super(type);
  }
}
