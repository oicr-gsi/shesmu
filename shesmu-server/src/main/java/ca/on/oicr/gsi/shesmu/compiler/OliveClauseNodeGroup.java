package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class OliveClauseNodeGroup extends OliveClauseNodeBaseBy<GroupNode> {

	public OliveClauseNodeGroup(int line, int column, List<GroupNode> groups, List<String> discriminators) {
		super("Group", line, column, groups, discriminators);
	}

	@Override
	public void render(RootBuilder builder, BaseOliveBuilder oliveBuilder,
			Map<String, OliveDefineBuilder> definitions) {
		final Set<String> freeVariables = new HashSet<>();
		children().forEach(group -> group.collectFreeVariables(freeVariables));

		oliveBuilder.line(line);
		final RegroupVariablesBuilder regroup = oliveBuilder.group(oliveBuilder.loadableValues()
				.filter(value -> freeVariables.contains(value.name())).toArray(LoadableValue[]::new));

		discriminators().forEach(discriminator -> {
			regroup.addKey(discriminator.type().asmType(), discriminator.name(), context -> {
				context.loadStream();
				context.methodGen().invokeVirtual(context.streamType(),
						new Method(discriminator.name(), discriminator.type().asmType(), new Type[] {}));
			});
		});
		children().forEach(group -> group.render(regroup, builder));
		regroup.finish();

		oliveBuilder.measureFlow(builder.sourcePath(), line, column);
	}
}
