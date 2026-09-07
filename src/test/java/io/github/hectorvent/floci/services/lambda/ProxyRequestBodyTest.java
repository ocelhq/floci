package io.github.hectorvent.floci.services.lambda;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyRequestBodyTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void aJsonBodyGoesInAsText() {
        ObjectNode event = mapper.createObjectNode();

        ProxyRequestBody.put(event, "{\"method\":\"DELETE\"}".getBytes(StandardCharsets.UTF_8), "application/json");

        assertEquals("{\"method\":\"DELETE\"}", event.get("body").asText());
        assertFalse(event.get("isBase64Encoded").asBoolean());
    }

    @Test
    void anOctetStreamBodyIsBase64EncodedByteForByte() {
        byte[] body = new byte[256];
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) i;
        }
        ObjectNode event = mapper.createObjectNode();

        ProxyRequestBody.put(event, body, "application/octet-stream");

        assertTrue(event.get("isBase64Encoded").asBoolean());
        assertEquals(Base64.getEncoder().encodeToString(body), event.get("body").asText());
    }

    @Test
    void aBodyWithoutAContentTypeIsBase64Encoded() {
        ObjectNode event = mapper.createObjectNode();

        ProxyRequestBody.put(event, new byte[] {(byte) 0xff, 0}, null);

        assertTrue(event.get("isBase64Encoded").asBoolean());
    }

    @Test
    void noBodyIsAJsonNull() {
        ObjectNode event = mapper.createObjectNode();

        ProxyRequestBody.put(event, new byte[0], "application/json");

        assertTrue(event.get("body").isNull());
        assertFalse(event.get("isBase64Encoded").asBoolean());
    }
}
