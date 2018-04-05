package ca.on.oicr.gsi.shesmu.actions.guanyin;

import java.security.MessageDigest;
import java.util.OptionalLong;

import javax.xml.bind.DatatypeConverter;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.Action;
import ca.on.oicr.gsi.shesmu.ActionState;
import ca.on.oicr.gsi.shesmu.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.Throttler;
import ca.on.oicr.gsi.shesmu.actions.util.JsonParameterised;
import io.prometheus.client.Counter;

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

	private final String category;
	private final String drmaaPsk;
	private final String drmaaUrl;
	private final String name;
	private final ObjectNode parameters = RuntimeSupport.MAPPER.createObjectNode();
	private OptionalLong reportRecordId = OptionalLong.empty();
	private final String rootDirectory;
	private final String version;
	private final String 观音Url;

	public RunReport(String 觀音Url, String drmaaUrl, String drmaaPsk, String rootDirectory, String category, String name,
			String version) {
		super();
		this.drmaaUrl = drmaaUrl;
		this.drmaaPsk = drmaaPsk;
		观音Url = 觀音Url;
		this.rootDirectory = rootDirectory;
		this.category = category;
		this.name = name;
		this.version = version;
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
		if (category == null) {
			if (other.category != null) {
				return false;
			}
		} else if (!category.equals(other.category)) {
			return false;
		}
		if (drmaaUrl == null) {
			if (other.drmaaUrl != null) {
				return false;
			}
		} else if (!drmaaUrl.equals(other.drmaaUrl)) {
			return false;
		}
		if (观音Url == null) {
			if (other.观音Url != null) {
				return false;
			}
		} else if (!观音Url.equals(other.观音Url)) {
			return false;
		}
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		if (parameters == null) {
			if (other.parameters != null) {
				return false;
			}
		} else if (!parameters.equals(other.parameters)) {
			return false;
		}
		if (rootDirectory == null) {
			if (other.rootDirectory != null) {
				return false;
			}
		} else if (!rootDirectory.equals(other.rootDirectory)) {
			return false;
		}
		if (version == null) {
			if (other.version != null) {
				return false;
			}
		} else if (!version.equals(other.version)) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (category == null ? 0 : category.hashCode());
		result = prime * result + (drmaaUrl == null ? 0 : drmaaUrl.hashCode());
		result = prime * result + (观音Url == null ? 0 : 观音Url.hashCode());
		result = prime * result + (name == null ? 0 : name.hashCode());
		result = prime * result + (parameters == null ? 0 : parameters.hashCode());
		result = prime * result + (rootDirectory == null ? 0 : rootDirectory.hashCode());
		result = prime * result + (version == null ? 0 : version.hashCode());
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
		final HttpPost request = new HttpPost(
				String.format("%s/reportdb/record_parameters?name=%s&version=%s", 观音Url, name, version));
		try {
			request.setEntity(new StringEntity(RuntimeSupport.MAPPER.writeValueAsString(parameters),
					ContentType.APPLICATION_JSON));
		} catch (final Exception e) {
			e.printStackTrace();
			return ActionState.FAILED;
		}
		try (CloseableHttpResponse response = ReportActionRepository.HTTP_CLIENT.execute(request);
				AutoCloseable timer = 观音RequestTime.start(观音Url)) {
			final ReportDto[] results = RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(),
					ReportDto[].class);
			if (results.length > 0) {
				reportRecordId = OptionalLong.of(results[0].getId());
				return ActionState.SUCCEEDED;
			}
		} catch (final Exception e) {
			e.printStackTrace();
			观音RequestErrors.labels(观音Url).inc();
			return ActionState.FAILED;
		}
		final HttpPost drmaaRequest = new HttpPost(String.format("%s/run", drmaaUrl));
		try {
			final ObjectNode drmaaParameters = RuntimeSupport.MAPPER.createObjectNode();
			drmaaParameters.put("drmaa_remote_command",
					String.format("%s/reports/%s/%s-%s", rootDirectory, category, name, version));
			drmaaParameters.putArray("drmaa_v_argv").add(RuntimeSupport.MAPPER.writeValueAsString(parameters));
			drmaaParameters.putArray("drmaa_v_env").add("GUANYIN=" + 观音Url);
			final String body = RuntimeSupport.MAPPER.writeValueAsString(drmaaParameters);
			final MessageDigest digest = MessageDigest.getInstance("SHA-1");
			digest.digest(drmaaPsk.getBytes());
			digest.digest(body.getBytes());
			drmaaRequest.addHeader("Authorization", "signed " + DatatypeConverter.printHexBinary(digest.digest()));
			drmaaRequest.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
		} catch (final Exception e) {
			e.printStackTrace();
			return ActionState.FAILED;
		}
		try (CloseableHttpResponse response = ReportActionRepository.HTTP_CLIENT.execute(drmaaRequest);
				AutoCloseable timer = drmaaRequestTime.start(drmaaUrl)) {
			final String result = RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(), String.class);
			final ActionState state = ActionState.valueOf(result);
			return state == null ? ActionState.UNKNOWN : state;
		} catch (final Exception e) {
			e.printStackTrace();
			drmaaRequestErrors.labels(drmaaUrl).inc();
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

	@Override
	public ObjectNode toJson(ObjectMapper mapper) {
		final ObjectNode node = mapper.createObjectNode();
		node.put("type", "guanyin-report");
		node.put("name", name);
		node.put("category", category);
		node.put("version", version);
		node.set("parameters", parameters);
		reportRecordId.ifPresent(id -> node.put("url", String.format("%s/reportdb/record/%d", 观音Url, id)));
		return node;
	}

}
