package ca.on.oicr.gsi.shesmu.gsistd.niassa;

import static ca.on.oicr.gsi.shesmu.gsistd.niassa.IniParam.BOOLEAN;
import static ca.on.oicr.gsi.shesmu.gsistd.niassa.IniParam.INTEGER;
import static ca.on.oicr.gsi.shesmu.gsistd.niassa.IniParam.STRING;
import static ca.on.oicr.gsi.shesmu.gsistd.niassa.IniParam.unitCorrectedInteger;

import java.util.stream.Stream;

import org.objectweb.asm.Type;

import ca.on.oicr.gsi.shesmu.Imyhat;

/**
 * Definitions of all the types of workflows
 *
 * These do not map exactly to Niassa's concept of a workflow. All this code
 * cares about is whether the types of the parameters in the INI are the same.
 * Any workflows that take the same parameters can share on entry here.
 *
 */
public enum WorkflowType {
	CELL_RANGER(Type.getType(WorkflowActionCellRanger.class), //
			standard(WorkflowAction.lanes(//
					Imyhat.tuple(Imyhat.STRING, Imyhat.INTEGER, Imyhat.STRING), //
					Imyhat.STRING, //
					Imyhat.tuple(Imyhat.STRING, Imyhat.STRING, Imyhat.STRING), //
					Imyhat.DATE, //
					Imyhat.STRING), //
					new IniParam("run_directory", "run_folder", STRING), //
					new IniParam("flowcell", STRING), //
					new IniParam("cellranger", STRING), //
					new IniParam("memory", unitCorrectedInteger(1024 * 1024)), //
					new IniParam("read_ends", INTEGER), //
					new IniParam("usebasesmask", false, STRING), //
					new IniParam("bcl_to_fastq_path", "bcl2fastqpath", STRING) //
			));

	private static WorkflowParameterDefinition[] standard(WorkflowParameterDefinition... params) {
		return Stream.concat(Stream.of(params), Stream.of(new IniParam("manual_output", BOOLEAN), //
				new IniParam("queue", false, STRING), //
				new IniParam("output_prefix", STRING)//
		)).toArray(WorkflowParameterDefinition[]::new);
	}

	private final WorkflowParameterDefinition[] definitions;
	private final Type type;

	private WorkflowType(WorkflowParameterDefinition... definitions) {
		this(Type.getType(WorkflowAction.class), definitions);
	}

	private WorkflowType(Type type, WorkflowParameterDefinition... definitions) {
		this.type = type;
		this.definitions = definitions;
	}

	public Stream<WorkflowParameterDefinition> parameters() {
		return Stream.of(definitions);
	}

	public Type type() {
		return type;
	}

}
