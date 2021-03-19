package ca.on.oicr.gsi.shesmu.runscanner;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class RunScannerPluginType extends PluginFileType<RunScannerClient> {

  private static final Map<Pair<Integer, Boolean>, Set<Set<Long>>> LANE_GEOMETRY_CACHE =
      new ConcurrentHashMap<>();

  /**
   * Create a set of sets, where all lanes that are merged are in a set together
   *
   * @param format the number of lanes and whether the lanes are joined or not
   */
  private static Set<Set<Long>> createFlowCellLayout(Pair<Integer, Boolean> format) {
    if (format.second()) {
      return Set.of(
          LongStream.rangeClosed(1, format.first())
              .boxed()
              .collect(Collectors.toCollection(TreeSet::new)));
    } else {
      return LongStream.rangeClosed(1, format.first())
          .mapToObj(Set::of)
          .collect(Collectors.toSet());
    }
  }

  @ShesmuMethod(
      name = "flowcell_geometry",
      description =
          "Generate a flowcell geometry for the number of lanes specified depending on whether they are joined together or separate.")
  public static Set<Set<Long>> getFlowcellLayout(
      @ShesmuParameter(description = "The number of lanes in the flowcell. Must be > 0.")
          long laneCount,
      @ShesmuParameter(description = "Whether all lanes are joined into a single physical lane.")
          boolean isJoined) {
    return LANE_GEOMETRY_CACHE.computeIfAbsent(
        new Pair<>((int) laneCount, isJoined), RunScannerPluginType::createFlowCellLayout);
  }

  @ShesmuMethod(
      name = "is_workflow_type_joined",
      description =
          "Check if a sequencing workflow type has a flow cell where all the lanes are joined or not.")
  public static boolean isWorkflowTypeJoined(
      @ShesmuParameter(description = "The type of the workflow") String workflowType) {
    return workflowType.equals("NovaSeqStandard") || workflowType.equals("OnBoardClustering");
  }

  public RunScannerPluginType() {
    super(MethodHandles.lookup(), RunScannerClient.class, ".runscanner", "runscanner");
  }

  @Override
  public RunScannerClient create(
      Path filePath, String instanceName, Definer<RunScannerClient> definer) {
    return new RunScannerClient(filePath, instanceName);
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    // No actions.
  }
}
