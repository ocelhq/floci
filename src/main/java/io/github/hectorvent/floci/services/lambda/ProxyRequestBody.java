package io.github.hectorvent.floci.services.lambda;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.core.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * Writes a request body into a proxy event the way API Gateway and Function URLs do: a body of
 * a text content type goes in as UTF-8 with {@code isBase64Encoded} false, anything else is
 * base64-encoded so bytes survive the JSON envelope untouched.
 */
public final class ProxyRequestBody {

    private static final Set<String> TEXT_CONTENT_TYPES = Set.of(
            MediaType.TEXT_PLAIN,
            MediaType.TEXT_HTML,
            "text/csv",
            MediaType.TEXT_XML,
            MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML,
            "application/javascript",
            "application/graphql");

    private ProxyRequestBody() {
    }

    public static void put(ObjectNode event, byte[] body, String contentType) {
        if (body == null || body.length == 0) {
            event.putNull("body");
            event.put("isBase64Encoded", false);
            return;
        }
        boolean text = isText(contentType);
        event.put("body", text
                ? new String(body, StandardCharsets.UTF_8)
                : Base64.getEncoder().encodeToString(body));
        event.put("isBase64Encoded", !text);
    }

    public static boolean isText(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        try {
            MediaType mediaType = MediaType.valueOf(contentType);
            String type = (mediaType.getType() + "/" + mediaType.getSubtype()).toLowerCase(Locale.ROOT);
            if (mediaType.getParameters().isEmpty()) {
                return TEXT_CONTENT_TYPES.contains(type);
            }
            return (MediaType.TEXT_PLAIN.equals(type) || MediaType.APPLICATION_JSON.equals(type))
                    && mediaType.getParameters().size() == 1
                    && StandardCharsets.UTF_8.name().equalsIgnoreCase(mediaType.getParameters().get("charset"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
