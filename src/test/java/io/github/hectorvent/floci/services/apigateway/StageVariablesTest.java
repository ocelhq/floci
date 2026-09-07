package io.github.hectorvent.floci.services.apigateway;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StageVariablesTest {

    @Test
    void resolvesAStageVariableInsideALambdaIntegrationUri() {
        String uri = "arn:aws:apigateway:us-east-1:lambda:path/2021-11-15/functions/"
                + "arn:aws:lambda:us-east-1:000000000000:function:${stageVariables.entry}/response-streaming-invocations";

        String resolved = StageVariables.substitute(uri, Map.of("entry", "web-r7", "assets", "r7"));

        assertEquals("arn:aws:apigateway:us-east-1:lambda:path/2021-11-15/functions/"
                + "arn:aws:lambda:us-east-1:000000000000:function:web-r7/response-streaming-invocations", resolved);
    }

    @Test
    void resolvesEveryReferenceAndLeavesTheRestOfTheUriAlone() {
        String uri = "arn:aws:apigateway:us-east-1:s3:path/bucket/${stageVariables.assets}/_next/static/{proxy}";

        assertEquals("arn:aws:apigateway:us-east-1:s3:path/bucket/r7/_next/static/{proxy}",
                StageVariables.substitute(uri, Map.of("assets", "r7")));
    }

    @Test
    void anUnknownVariableResolvesToEmpty() {
        assertEquals("function:/invocations",
                StageVariables.substitute("function:${stageVariables.missing}/invocations", Map.of()));
        assertEquals("function:/invocations",
                StageVariables.substitute("function:${stageVariables.missing}/invocations", null));
    }

    @Test
    void aValueWithRegexMetacharactersIsInsertedLiterally() {
        assertEquals("x/$1\\d/y",
                StageVariables.substitute("x/${stageVariables.v}/y", Map.of("v", "$1\\d")));
    }

    @Test
    void nullUriStaysNull() {
        assertNull(StageVariables.substitute(null, Map.of("a", "b")));
    }
}
