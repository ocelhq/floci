package io.github.hectorvent.floci.services.lambda;

import io.github.hectorvent.floci.services.lambda.model.InvokeResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpIntegrationResponseTest {

    static byte[] envelope(String prelude, byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(prelude.getBytes(StandardCharsets.UTF_8));
        out.writeBytes(new byte[8]);
        out.writeBytes(body);
        return out.toByteArray();
    }

    static InvokeResult streamed(String contentType, byte[] payload) {
        InvokeResult result = new InvokeResult(200, null, payload, null, "req-1");
        result.setResponseMode(HttpIntegrationResponse.STREAMING_RESPONSE_MODE);
        result.setResponseContentType(contentType);
        return result;
    }

    @Test
    void unpacksThePreludeAndTheBodyThatFollowsTheSeparator() {
        byte[] payload = envelope(
                "{\"statusCode\":201,\"headers\":{\"Content-Type\":\"application/json\",\"X-Release\":\"r7\"},"
                        + "\"cookies\":[\"a=1; Path=/\"]}",
                "{\"id\":\"7\"}".getBytes(StandardCharsets.UTF_8));

        HttpIntegrationResponse response = HttpIntegrationResponse.unpack(
                streamed(HttpIntegrationResponse.HTTP_INTEGRATION_CONTENT_TYPE, payload)).orElseThrow();

        assertEquals(201, response.statusCode());
        assertEquals("r7", response.headers().get("X-Release"));
        assertEquals("application/json", response.contentType());
        assertEquals(List.of("a=1; Path=/"), response.cookies());
        assertEquals("{\"id\":\"7\"}", new String(response.body(), StandardCharsets.UTF_8));
    }

    @Test
    void aBodyContainingNulBytesAfterTheSeparatorIsDeliveredIntact() {
        byte[] body = {1, 0, 0, 0, 0, 0, 0, 0, 0, 2};
        byte[] payload = envelope("{\"statusCode\":200,\"headers\":{}}", body);

        HttpIntegrationResponse response = HttpIntegrationResponse.unpack(
                streamed(HttpIntegrationResponse.HTTP_INTEGRATION_CONTENT_TYPE, payload)).orElseThrow();

        assertArrayEquals(body, response.body());
    }

    @Test
    void anEmptyBodyAfterThePreludeIsAnEmptyEntity() {
        byte[] payload = envelope("{\"statusCode\":204,\"headers\":{}}", new byte[0]);

        HttpIntegrationResponse response = HttpIntegrationResponse.unpack(
                streamed(HttpIntegrationResponse.HTTP_INTEGRATION_CONTENT_TYPE, payload)).orElseThrow();

        assertEquals(204, response.statusCode());
        assertEquals(0, response.body().length);
    }

    @Test
    void aStreamedPayloadOfAnotherContentTypeIsTheBodyOfA200() {
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);

        HttpIntegrationResponse response = HttpIntegrationResponse.unpack(
                streamed("text/plain", payload)).orElseThrow();

        assertEquals(200, response.statusCode());
        assertEquals("text/plain", response.contentType());
        assertArrayEquals(payload, response.body());
    }

    @Test
    void aBufferedResponseIsLeftToTheJsonPath() {
        InvokeResult buffered = new InvokeResult(200, null,
                "{\"statusCode\":200,\"body\":\"ok\"}".getBytes(StandardCharsets.UTF_8), null, "req-1");

        assertTrue(HttpIntegrationResponse.unpack(buffered).isEmpty());
    }

    @Test
    void aFunctionErrorIsLeftToTheJsonPath() {
        InvokeResult failed = new InvokeResult(200, "Unhandled",
                "{\"errorMessage\":\"boom\"}".getBytes(StandardCharsets.UTF_8), null, "req-1");
        failed.setResponseMode(HttpIntegrationResponse.STREAMING_RESPONSE_MODE);
        failed.setResponseContentType(HttpIntegrationResponse.HTTP_INTEGRATION_CONTENT_TYPE);

        assertTrue(HttpIntegrationResponse.unpack(failed).isEmpty());
    }

    @Test
    void anEnvelopeWithoutASeparatorIsLeftToTheJsonPath() {
        Optional<HttpIntegrationResponse> response = HttpIntegrationResponse.unpack(streamed(
                HttpIntegrationResponse.HTTP_INTEGRATION_CONTENT_TYPE,
                "{\"statusCode\":200}".getBytes(StandardCharsets.UTF_8)));

        assertTrue(response.isEmpty());
    }
}
