package ca.on.oicr.gsi.shesmu.guanyin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.OptionalLong;
import java.util.Scanner;
import java.util.stream.Stream;

import javax.xml.bind.DatatypeConverter;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.Action;
import ca.on.oicr.gsi.shesmu.ActionState;
import ca.on.oicr.gsi.shesmu.Throttler;
import ca.on.oicr.gsi.shesmu.guanyin.ReportActionRepository.GuanyinFile;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.util.actions.JsonParameterised;
import io.prometheus.client.Counter;

/**
 * Action to query/launch a report using Guanyin and DRMAAWS
 *
 * The action will first query Guanyin to see if the report has been run
 * previously. If not, attempt to run it using DRMAAWS.
 */
public class RunReport extends Action implements JsonParameterised {
	private static final Counter drmaaRequestErrors = Counter
			.build("shesmu_drmaa_request_errors", "The number of errors trying to countact the DRMAA web service")
			.labelNames("target").register();
	private static final LatencyHistogram drmaaRequestTime = new LatencyHistogram("shesmu_drmaa_request_time",
			"The request time latency to launch a remote action.", "target");
	private static final Counter 观音RequestErrors = Counter
			.build("shesmu_guanyin_request_errors", "The number of errors trying to countact the Guanyin web service.")
			.labelNames("target").register();
	private static final LatencyHistogram 观音RequestTime = new LatencyHistogram("shesmu_guanyin_request_time",
			"The request time latency to launch a remote action.", "target");

	private final GuanyinFile owner;
	private final ObjectNode parameters;
	private final long reportId;
	private final String reportName;
	private OptionalLong reportRecordId = OptionalLong.empty();
	private final ObjectNode rootParameters = RuntimeSupport.MAPPER.createObjectNode();

	public RunReport(GuanyinFile owner, long reportId, String reportName) {
		super("guanyin-report");
		this.owner = owner;
		this.reportId = reportId;
		this.reportName = reportName;
		parameters = rootParameters.putObject("parameters");
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final RunReport other = (RunReport) obj;
		if (owner != other.owner) {
			return false;
		}
		if (parameters == null) {
			if (other.parameters != null) {
				return false;
			}
		} else if (!parameters.equals(other.parameters)) {
			return false;
		}
		if (reportId != other.reportId) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (owner == null ? 0 : owner.hashCode());
		result = prime * result + (parameters == null ? 0 : parameters.hashCode());
		result = prime * result + Long.hashCode(reportId);
		return result;
	}

	@Override
	public ObjectNode parameters() {
		return parameters;
	}

	@Override
	public ActionState perform() {
		if (Throttler.anyOverloaded("all", "guanyin", "drmaa")) {
			return ActionState.THROTTLED;
		}

		// Query Guanyin to see if the record already exists
		StringEntity body;
		try {
			body = new StringEntity(RuntimeSupport.MAPPER.writeValueAsString(rootParameters),
					ContentType.APPLICATION_JSON);
		} catch (final Exception e) {
			e.printStackTrace();
			return ActionState.FAILED;
		}

		boolean create = false;
		final HttpPost request = new HttpPost(
				String.format("%s/reportdb/record_parameters?report=%d", owner.观音Url(), reportId));
		request.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
		request.setEntity(body);
		try (AutoCloseable timer = 观音RequestTime.start(owner.观音Url());
				CloseableHttpResponse response = ReportActionRepository.HTTP_CLIENT.execute(request)) {
			if (response.getStatusLine().getStatusCode() != 200) {
				showError(response, "Error from Guanyin: ");
				观音RequestErrors.labels(owner.观音Url()).inc();
				return ActionState.FAILED;
			}
			final RecordDto[] results = RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(),
					RecordDto[].class);
			if (results.length > 0) {
				final RecordDto record = Stream.of(results)
						.sorted(Comparator.comparing(RecordDto::getGenerated).reversed()).findFirst().get();
				reportRecordId = OptionalLong.of(record.getId());
				if (record.isFinished()) {
					return ActionState.SUCCEEDED;
				}
			} else {
				create = true;
			}
		} catch (final Exception e) {
			e.printStackTrace();
			观音RequestErrors.labels(owner.观音Url()).inc();
			return ActionState.FAILED;
		}
		// At this point, either it exists and isn't complete or it doesn't exist.
		// Create it if it doesn't exist
		if (create) {
			final HttpPost createRequest = new HttpPost(
					String.format("%s/reportdb/record_start?report=%d", owner.观音Url(), reportId));
			createRequest.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
			createRequest.setEntity(body);
			try (AutoCloseable timer = 观音RequestTime.start(owner.观音Url());
					CloseableHttpResponse response = ReportActionRepository.HTTP_CLIENT.execute(createRequest)) {
				if (response.getStatusLine().getStatusCode() != 200) {
					showError(response, "Error from Guanyin: ");
					观音RequestErrors.labels(owner.观音Url()).inc();
					return ActionState.FAILED;
				}
				reportRecordId = OptionalLong.of(
						RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(), CreateDto.class).getId());
			} catch (final Exception e) {
				e.printStackTrace();
				观音RequestErrors.labels(owner.观音Url()).inc();
				return ActionState.FAILED;
			}
		}
		// Now that exists, try to run it via DRMAA
		final HttpPost drmaaRequest = new HttpPost(String.format("%s/run", owner.drmaaUrl()));
		try {
			final ObjectNode drmaaParameters = RuntimeSupport.MAPPER.createObjectNode();
			drmaaParameters.put("drmaa_remote_command", owner.script());
			drmaaParameters.putArray("drmaa_v_argv")
					.add(String.format("%s/reportdb/record/%d", owner.观音Url(), reportRecordId.getAsLong()));
			drmaaParameters.put("drmaa_output_path",
					String.format(":$drmaa_hd_ph$/logs/reports/report%d-%d.out", reportId, reportRecordId.getAsLong()));
			drmaaParameters.put("drmaa_error_path",
					String.format(":$drmaa_hd_ph$/logs/reports/report%d-%d.err", reportId, reportRecordId.getAsLong()));
			drmaaParameters.put("drmaa_native_specification", "-l h_vmem=2g");
			final byte[] drmaaBody = RuntimeSupport.MAPPER.writeValueAsBytes(drmaaParameters);
			final MessageDigest digest = MessageDigest.getInstance("SHA-1");
			digest.update(owner.drmaaPsk().getBytes(StandardCharsets.UTF_8));
			digest.update(drmaaBody);
			drmaaRequest.setHeader("Accept", "application/json");
			drmaaRequest.addHeader("Authorization", "signed " + DatatypeConverter.printHexBinary(digest.digest()));
			drmaaRequest.setEntity(new ByteArrayEntity(drmaaBody, ContentType.APPLICATION_JSON));
		} catch (final Exception e) {
			e.printStackTrace();
			return ActionState.FAILED;
		}
		try (AutoCloseable timer = drmaaRequestTime.start(owner.drmaaUrl());
				CloseableHttpResponse response = ReportActionRepository.HTTP_CLIENT.execute(drmaaRequest)) {
			if (response.getStatusLine().getStatusCode() != 200) {
				showError(response, "Error from DRMAA: ");
				drmaaRequestErrors.labels(owner.drmaaUrl()).inc();
				return ActionState.FAILED;
			}
			final String result = RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(), String.class);
			final ActionState state = ActionState.valueOf(result);
			return state == null ? ActionState.UNKNOWN : state;
		} catch (final Exception e) {
			e.printStackTrace();
			drmaaRequestErrors.labels(owner.drmaaUrl()).inc();
			return ActionState.FAILED;
		}
	}

	@Override
	public int priority() {
		return 0;
	}

	@Override
	public long retryMinutes() {
		return 10;
	}

	private void showError(CloseableHttpResponse response, String prefix)
			throws UnsupportedOperationException, IOException {
		try (Scanner s = new Scanner(response.getEntity().getContent())) {
			s.useDelimiter("\\A");
			if (s.hasNext()) {
				System.err.print(prefix);
				System.err.println(s.next());
			}
		}
	}

	@Override
	public ObjectNode toJson(ObjectMapper mapper) {
		final ObjectNode node = mapper.createObjectNode();
		node.put("type", "guanyin-report");
		node.put("reportName", reportName);
		node.put("reportId", reportId);
		node.put("script", owner.script());
		node.set("parameters", parameters);
		reportRecordId.ifPresent(id -> node.put("url", String.format("%s/reportdb/record/%d", owner.观音Url(), id)));
		return node;
	}

}
