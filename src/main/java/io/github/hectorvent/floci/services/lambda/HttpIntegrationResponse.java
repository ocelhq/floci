package io.github.hectorvent.floci.services.lambda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hectorvent.floci.services.lambda.model.InvokeResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The HTTP response a streaming function wrote through the Runtime API.
 *
 * <p>A function that answers with {@code Lambda-Runtime-Function-Response-Mode: streaming}
 * and the {@code application/vnd.awslambda.http-integration-response} content type writes a
 * JSON prelude ({@code statusCode}, {@code headers}, {@code cookies}), eight NUL bytes, then
 * the raw body. Function URLs and API Gateway response-streaming integrations deliver that
 * as the HTTP response; a streamed payload of any other content type is the body of a 200.
 *
 * @param statusCode  the HTTP status to answer with
 * @param headers     response headers in prelude order
 * @param cookies     {@code Set-Cookie} values named by the prelude
 * @param body        the raw body bytes, possibly empty
 * @param contentType the content type the prelude names, or the runtime's when there is no prelude
 */
public record HttpIntegrationResponse(int statusCode, Map<String, String> headers, List<String> cookies,
                                      byte[] body, String contentType) {

    public static final String RESPONSE_MODE_HEADER = "Lambda-Runtime-Function-Response-Mode";
    public static final String STREAMING_RESPONSE_MODE = "streaming";
    public static final String HTTP_INTEGRATION_CONTENT_TYPE = "application/vnd.awslambda.http-integration-response";

    private static final byte[] PRELUDE_SEPARATOR = new byte[8];
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Unpacks a streamed response; empty when the function answered in buffered mode, or with a
     * function error, so callers keep their existing JSON handling for those.
     */
    public static Optional<HttpIntegrationResponse> unpack(InvokeResult result) {
        if (result == null || result.getFunctionError() != null
                || !STREAMING_RESPONSE_MODE.equalsIgnoreCase(result.getResponseMode())) {
            return Optional.empty();
        }
        byte[] payload = result.getPayload() != null ? result.getPayload() : new byte[0];
        String contentType = result.getResponseContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith(HTTP_INTEGRATION_CONTENT_TYPE)) {
            return Optional.of(new HttpIntegrationResponse(200, Map.of(), List.of(), payload,
                    contentType != null ? contentType : "application/octet-stream"));
        }
        int separator = indexOfSeparator(payload);
        if (separator < 0) {
            return Optional.empty();
        }
        JsonNode prelude;
        try {
            prelude = MAPPER.readTree(Arrays.copyOfRange(payload, 0, separator));
        } catch (Exception e) {
            return Optional.empty();
        }
        if (!prelude.isObject()) {
            return Optional.empty();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        JsonNode headerNode = prelude.get("headers");
        if (headerNode != null && headerNode.isObject()) {
            headerNode.fields().forEachRemaining(e -> headers.put(e.getKey(), e.getValue().asText()));
        }
        List<String> cookies = new ArrayList<>();
        JsonNode cookieNode = prelude.get("cookies");
        if (cookieNode != null && cookieNode.isArray()) {
            cookieNode.forEach(c -> cookies.add(c.asText()));
        }
        byte[] body = Arrays.copyOfRange(payload, separator + PRELUDE_SEPARATOR.length, payload.length);
        String bodyType = headers.entrySet().stream()
                .filter(e -> "content-type".equalsIgnoreCase(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        return Optional.of(new HttpIntegrationResponse(prelude.path("statusCode").asInt(200), headers, cookies,
                body, bodyType));
    }

    private static int indexOfSeparator(byte[] payload) {
        outer:
        for (int i = 0; i + PRELUDE_SEPARATOR.length <= payload.length; i++) {
            for (int j = 0; j < PRELUDE_SEPARATOR.length; j++) {
                if (payload[i + j] != 0) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
