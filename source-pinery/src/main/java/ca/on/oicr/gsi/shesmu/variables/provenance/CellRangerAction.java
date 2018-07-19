package ca.on.oicr.gsi.shesmu.variables.provenance;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.Tuple;

public class CellRangerAction extends SeqWareWorkflowAction<CellRangerAction.CellRangerLimsKey> {

	final class CellRangerLimsKey implements LimsKey {
		private final String groupId;
		private final int ius_1;
		private final String ius_2;
		private final ZonedDateTime lastModified;
		private final String provider;
		private final String sampleId;
		private final String version;

		private CellRangerLimsKey(Tuple t) {
			final Tuple ius = (Tuple) t.get(0);
			ius_1 = ((Long) ius.get(1)).intValue();
			ius_2 = (String) ius.get(2);

			final Tuple lims = (Tuple) t.get(1);
			provider = (String) lims.get(0);
			sampleId = (String) lims.get(1);
			version = (String) lims.get(2);

			lastModified = ZonedDateTime.ofInstant((Instant) t.get(2), ZoneId.of("Z"));
			groupId = (String) t.get(3);
		}

		@Override
		public String getId() {
			return sampleId;
		}

		@Override
		public ZonedDateTime getLastModified() {
			return lastModified;
		}

		@Override
		public String getProvider() {
			return provider;
		}

		@Override
		public String getVersion() {
			return version;
		}
	}

	private List<CellRangerLimsKey> limsKeys;

	public CellRangerAction(long workflowAccession, long[] previousAccessions, String jarPath, String settingsPath,
			String[] services) {
		super(workflowAccession, previousAccessions, jarPath, settingsPath, services);
	}

	@RuntimeInterop
	public void lanes(Set<Tuple> tuples) {
		limsKeys = tuples.stream().map(CellRangerLimsKey::new).sorted(LIMS_KEY_COMPARATOR).collect(Collectors.toList());

	}

	@Override
	protected List<CellRangerLimsKey> limsKeys() {
		return limsKeys;
	}

	@Override
	protected void prepareIniForLimsKeys(Stream<Pair<Integer, CellRangerLimsKey>> stream) {
		ini.setProperty("lanes", stream.map(iusKey -> {
			final CellRangerLimsKey limsKey = iusKey.second();
			return Stream.of(limsKey.ius_1, // lane
					0, // lane SWID
					limsKey.ius_2, // barcode
					iusKey.first(), // IUS SWID
					limsKey.getId(), // sample name
					limsKey.groupId)// group ID
					.map(Object::toString)//
					.collect(Collectors.joining(","));
		}).collect(Collectors.joining("+")));
	}

}
