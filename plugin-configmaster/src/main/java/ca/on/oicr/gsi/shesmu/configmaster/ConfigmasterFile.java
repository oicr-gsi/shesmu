package ca.on.oicr.gsi.shesmu.configmaster;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFile;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ConfigmasterFile extends PluginFile {
  private Path djerba, dare, qcMapping;
  Map<String, Set<Workflows.Workflow>> pipelines;
  static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    MAPPER.registerModule(new JavaTimeModule());
  }

  ConfigmasterFile(Path fileName, String instanceName, Definer definer) {
    super(fileName, instanceName);
  }

  @ShesmuMethod
  public boolean needed_for_case(String pipeline_name, Set<String> deliverables, String workflow) {
    Case myCase = new Case(pipeline_name, deliverables);
    return myCase.isWorkflowRequired(Workflows.getByVersionedName(workflow, "1"));
  }

  @ShesmuMethod
  public Set<String> djerba_requirements(String pipeline_name, Set<String> deliverables) {
    Case myCase = new Case(pipeline_name, deliverables);
    return myCase.getDjerbaRequirements().stream()
        .map(Files.File::name)
        .collect(Collectors.toSet());
  }

  @Override
  public void configuration(SectionRenderer renderer) {}

  @Override
  public Optional<Integer> update() {
    try {
      JsonNode node = MAPPER.readTree(fileName().toFile());
      this.djerba = Path.of(node.get("djerba").asText());
      this.dare = Path.of(node.get("dare").asText());
      this.qcMapping = Path.of(node.get("qcMapping").asText());
      this.pipelines = new HashMap<>();

      ArrayNode pipelineNode = (ArrayNode) node.get("pipelines");
      Iterator<JsonNode> pipelineIterator = pipelineNode.elements();
      while (pipelineIterator.hasNext()) {
        ObjectNode pipeline = (ObjectNode) pipelineIterator.next();
        String pipelineName = pipeline.fieldNames().next();
        Set<Workflows.Workflow> workflows = new HashSet<>();
        ArrayNode pipelineWorkflows = (ArrayNode) pipeline.get(pipelineName);
        Iterator<JsonNode> pipelineWorkflowIterator = pipelineWorkflows.iterator();
        while (pipelineWorkflowIterator.hasNext()) {
          Workflows.Workflow realWorkflow;
          ObjectNode workflow = (ObjectNode) pipelineWorkflowIterator.next();
          ArrayNode inputs = (ArrayNode) workflow.get("inputs");
          ArrayNode outputs = (ArrayNode) workflow.get("outputs");
          String name = workflow.get("name").asText();
          realWorkflow =
              new Workflows.Workflow(
                  StreamSupport.stream(inputs.spliterator(), false)
                      .map(JsonNode::asText)
                      .collect(Collectors.toList()),
                  name,
                  "1",
                  StreamSupport.stream(outputs.spliterator(), false)
                      .map(JsonNode::asText)
                      .collect(Collectors.toList()));
          workflows.add(realWorkflow);
        }
        pipelines.put(pipelineName, workflows);
      }
    } catch (IOException ioe) {
      System.err.println("lol");
    }
    return Optional.empty();
  }
}
