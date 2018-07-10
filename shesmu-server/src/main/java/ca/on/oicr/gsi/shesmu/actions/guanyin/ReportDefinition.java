package ca.on.oicr.gsi.shesmu.actions.guanyin;

import static org.objectweb.asm.Type.LONG_TYPE;

import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ParameterDefinition;
import ca.on.oicr.gsi.shesmu.actions.rest.LaunchRemote;

public class ReportDefinition extends ActionDefinition {
	static final Type A_LAUNCH_REMOTE_TYPE = Type.getType(LaunchRemote.class);
	private static final Type A_STRING_TYPE = Type.getType(String.class);
	private static final Type ACTION_TYPE = Type.getType(RunReport.class);

	private static final Method CTOR = new Method("<init>", Type.VOID_TYPE,
			new Type[] { A_STRING_TYPE, A_STRING_TYPE, A_STRING_TYPE, A_STRING_TYPE, LONG_TYPE });

	private final String drmaaPsk;
	private final String drmaaUrl;
	private final long reportId;
	private final String script;
	private final String 观音Url;

	public ReportDefinition(String 观音Url, String drmaaUrl, String drmaaPsk, String script, long reportId, String name,
			String version, Stream<ParameterDefinition> parameters) {
		super(name + "_" + version.replaceAll("[^A-Za-z0-9_]", "_"), ACTION_TYPE,
				String.format("Runs report %s-%s (%d) in %s from %s using %s.", name, version, reportId, script, 观音Url,
						drmaaUrl),
				parameters);
		this.观音Url = 观音Url;
		this.drmaaUrl = drmaaUrl;
		this.drmaaPsk = drmaaPsk;
		this.script = script;
		this.reportId = reportId;
	}

	@Override
	public void initialize(GeneratorAdapter methodGen) {
		methodGen.newInstance(ACTION_TYPE);
		methodGen.dup();
		methodGen.push(观音Url);
		methodGen.push(drmaaUrl);
		methodGen.push(drmaaPsk);
		methodGen.push(script);
		methodGen.push(reportId);
		methodGen.invokeConstructor(ACTION_TYPE, CTOR);
	}

}
