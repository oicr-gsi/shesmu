package ca.on.oicr.gsi.shesmu.gsistd.niassa;

import static ca.on.oicr.gsi.shesmu.gsistd.niassa.IniParam.BOOLEAN;
import static ca.on.oicr.gsi.shesmu.gsistd.niassa.IniParam.INTEGER;
import static ca.on.oicr.gsi.shesmu.gsistd.niassa.IniParam.STRING;
import static ca.on.oicr.gsi.shesmu.gsistd.niassa.IniParam.unitCorrectedInteger;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.provenance.model.IusLimsKey;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;

/**
 * Definitions of all the types of workflows
 *
 * These do not map exactly to Niassa's concept of a workflow. All this code
 * cares about is whether the types of the parameters in the INI are the same.
 * Any workflows that take the same parameters can share on entry here.
 *
 */
public enum WorkflowType {
	CELL_RANGER(standard(lanes(//
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
	)) {

		@Override
		public void define(JavaGenericTypeSafeInstantiator consumer, long workflowAccession, long[] previousAccessions, String jarPath,
				String settingsPath, String[] services) {
			consumer.define(WorkflowActionCellRanger.class, () -> new WorkflowActionCellRanger(workflowAccession,
					previousAccessions, jarPath, settingsPath, services));
		}

	};

	private static ActionParameterDefinition[] standard(ActionParameterDefinition... params) {
		return Stream.concat(Stream.of(params), Stream.of(new IniParam("manual_output", BOOLEAN), //
				new IniParam("queue", false, STRING), //
				new IniParam("output_prefix", STRING)//
		)).toArray(ActionParameterDefinition[]::new);
	}

	/**
	 * Create a <tt>lanes</tt> parameter that can tie a workflow run to new IUSes
	 *
	 * To use this, create a class that inherits from {@link WorkflowAction} that
	 * has a constructor that exactly matches the superclass constructor.
	 * <ol>
	 * <li>Create a new method called <tt>void lanes(Set&lt;Tuple&gt; info)</tt>
	 * that accepts a set of tuples as described by the <tt>lanes</tt> parameter and
	 * stores that information in a field.</li>
	 * <li>Override the {@link #limsKeys()} to return a collection of
	 * {@link LimsKey} based on this stored information</li>
	 * <li>Override {@link #prepareIniForLimsKeys(Stream)} to take a stream of
	 * {@link IusLimsKey} and update {@link #ini} as appropriate. The
	 * {@link LimsKey} values stored in the {@link IusLimsKey} are the ones provided
	 * earlier, so they can be cast to a class carrying supplemental
	 * information</li>
	 * </ol>
	 *
	 * @return
	 */
	public static final ActionParameterDefinition lanes(Imyhat... lanes) {
		return new ActionParameterDefinition() {

			@Override
			public String name() {
				return "lanes";
			}

			@Override
			public boolean required() {
				return true;
			}

			@Override
			public void store(Renderer renderer, Type type, int actionLocal, Consumer<Renderer> loadParameter) {
				renderer.methodGen().loadLocal(actionLocal);
				loadParameter.accept(renderer);
				renderer.methodGen().invokeVirtual(type,
						new Method("lanes", Type.VOID_TYPE, new Type[] { Type.getType(Set.class) }));
			}

			@Override
			public Imyhat type() {
				return Imyhat.tuple(lanes).asList();
			}
		};
	}

	private final ActionParameterDefinition[] definitions;

	private WorkflowType(ActionParameterDefinition... definitions) {
		this.definitions = definitions;
	}

	public final Stream<ActionParameterDefinition> parameters() {
		return Stream.of(definitions);
	}

	public interface JavaGenericTypeSafeInstantiator {
		<T extends WorkflowAction<K>, K extends LimsKey> void define(Class<T> clazz, Supplier<T> supplier);
	}

	public abstract void define(JavaGenericTypeSafeInstantiator consumer, long workflowAccession, long[] previousAccessions, String jarPath,
			String settingsPath, String[] services);
}
