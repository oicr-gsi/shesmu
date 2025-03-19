package ca.on.oicr.gsi.shesmu.configmaster;

import ca.on.oicr.gsi.shesmu.configmaster.Files.*;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class Workflows {
  public static final class Workflow {
    private final Set<File> inputs;
    private final String name;
    private final String version;
    private final Set<File> outputs;

    public Workflow(List<String> inputs, String name, String version, List<String> outputs) {
      this.name = name;
      this.version = version;
      Set<File> tempInputs = new HashSet<>(), tempOutputs = new HashSet<>();
      for (String input : inputs) {
        File potentialFile = Files.getByName(input);
        if (potentialFile == null) {
          potentialFile = new File(input);
        }
        tempInputs.add(potentialFile);
      }
      for (String output : outputs) {
        File potentialFile = Files.getByName(output);
        if (potentialFile == null) {
          potentialFile = new File(output);
        }
        tempOutputs.add(potentialFile);
      }
      this.inputs = tempInputs;
      this.outputs = tempOutputs;
      Files.files.addAll(this.inputs);
      Files.files.addAll(this.outputs);
      Workflows.workflows.add(this);
    }

    public Workflow(Set<File> inputs, String name, String version, Set<File> outputs) {
      this.inputs = inputs;
      this.name = name;
      this.version = version;
      this.outputs = outputs;
      Workflows.workflows.add(this);
    }

    public Set<File> inputs() {
      return inputs;
    }

    public String name() {
      return name;
    }

    public String version() {
      return version;
    }

    public Set<File> outputs() {
      return outputs;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (obj == null || obj.getClass() != this.getClass()) return false;
      var that = (Workflow) obj;
      return Objects.equals(this.inputs, that.inputs)
          && Objects.equals(this.name, that.name)
          && Objects.equals(this.version, that.version)
          && Objects.equals(this.outputs, that.outputs);
    }

    @Override
    public int hashCode() {
      return Objects.hash(inputs, name, version, outputs);
    }

    @Override
    public String toString() {
      return "Workflow["
          + "inputs="
          + inputs
          + ", "
          + "name="
          + name
          + ", "
          + "version="
          + version
          + ", "
          + "outputs="
          + outputs
          + ']';
    }
  }

  public static Set<Workflow> workflows = new HashSet<>();

  public static Set<Workflow> whatOutputs(File outputFile) {
    return workflows.stream()
        .filter(w -> w.outputs.contains(outputFile))
        .collect(Collectors.toSet());
  }

  public static Set<Workflow> getAllUpstreamFrom(Workflow workflow) {
    Set<Workflow> ret = new HashSet<>();

    for (File input : workflow.inputs()) {
      Set<Workflow> temp = new HashSet<>(whatOutputs(input));
      ret.addAll(temp);
      for (Workflow w : temp) {
        ret.addAll(getAllUpstreamFrom(w));
      }
    }
    return ret;
  }

  public static Workflow getByVersionedName(String name, String version) {
    return workflows.stream()
        .filter(w -> w.name().equals(name) && w.version().equals(version))
        .findFirst()
        .orElse(null);
  }
}
