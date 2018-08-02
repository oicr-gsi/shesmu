package ca.on.oicr.gsi.shesmu.variables.provenance;

import static ca.on.oicr.gsi.shesmu.variables.provenance.IniParam.BOOLEAN;
import static ca.on.oicr.gsi.shesmu.variables.provenance.IniParam.INTEGER;
import static ca.on.oicr.gsi.shesmu.variables.provenance.IniParam.STRING;
import static ca.on.oicr.gsi.shesmu.variables.provenance.IniParam.unitCorrectedInteger;

import java.util.stream.Stream;

import org.objectweb.asm.Type;

import ca.on.oicr.gsi.shesmu.Imyhat;

/**
 * Definitions of all the types of workflows in SeqWare
 *
 * These do not map exactly to SeqWare's concept of a workflow. All this code
 * cares about is whether the types of the parameters in the INI are the same.
 * Any workflows that take the same parameters can share on entry here.
 *
 */
public enum WorkflowType {
	CELL_RANGER(standard(SeqWareWorkflowAction.lanes(//
			Imyhat.tuple(Imyhat.STRING, Imyhat.INTEGER, Imyhat.STRING), //
			Imyhat.tuple(Imyhat.STRING, Imyhat.STRING, Imyhat.STRING), //
			Imyhat.DATE, //
			Imyhat.STRING), //
			new IniParam("run_directory", "runFolder", STRING), //
			new IniParam("flowcell", STRING), //
			new IniParam("cellranger", STRING), //
			new IniParam("memory", unitCorrectedInteger(1024 * 1024)), //
			new IniParam("read_ends", "readEnds", INTEGER), //
			new IniParam("usebasesmask", false, STRING), //
			new IniParam("bcl_to_fastq_path", "bcl2fastqpath", STRING) //
	));

	private static SeqWareParameterDefinition[] standard(SeqWareParameterDefinition... params) {
		return Stream.concat(Stream.of(params), Stream.of(new IniParam("manual_output", "manualOutput", BOOLEAN), //
				new IniParam("queue", false, STRING), //
				new IniParam("output_prefix", STRING)//
		)).toArray(SeqWareParameterDefinition[]::new);
	}

	private final SeqWareParameterDefinition[] definitions;
	private final Type type;

	private WorkflowType(SeqWareParameterDefinition... definitions) {
		this(Type.getType(SeqWareWorkflowAction.class), definitions);
	}

	private WorkflowType(Type type, SeqWareParameterDefinition... definitions) {
		this.type = type;
		this.definitions = definitions;
	}

	public Stream<SeqWareParameterDefinition> parameters() {
		return Stream.of(definitions);
	}

	public Type type() {
		return type;
	}

}
