package io.github.hectorvent.floci.services.lambda;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * An OS-only runtime (provided, provided.al2, provided.al2023) may ship its bootstrap in a
 * layer rather than in the deployment package: "If the root of your deployment package doesn't
 * contain a file named bootstrap, Lambda looks for the file in the function's layers"
 * (https://docs.aws.amazon.com/lambda/latest/dg/runtimes-custom.html). CreateFunction therefore
 * must not reject a provided.* package that has no bootstrap; a missing entrypoint surfaces as
 * Runtime.InvalidEntrypoint on invocation instead.
 */
@QuarkusTest
class LambdaProvidedRuntimeBootstrapIntegrationTest {

    private static byte[] zipWithoutBootstrap() throws Exception {
        return zip("lib/handler.so", new byte[] {0x7f, 'E', 'L', 'F'});
    }

    private static byte[] zip(String entryName, byte[] content) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private static String createBody(String functionName, String runtime, String handler, byte[] zip) {
        return """
            {
                "FunctionName": "%s",
                "Runtime": "%s",
                "Role": "arn:aws:iam::000000000000:role/lambda-role",
                "Handler": "%s",
                "Code": { "ZipFile": "%s" }
            }
            """.formatted(functionName, runtime, handler, Base64.getEncoder().encodeToString(zip));
    }

    @Test
    void createFunctionAcceptsProvidedRuntimeWithoutBootstrapInPackage() throws Exception {
        given()
            .contentType("application/json")
            .body(createBody("provided-bootstrap-in-layer", "provided.al2023", "bootstrap",
                    zipWithoutBootstrap()))
        .when()
            .post("/2015-03-31/functions")
        .then()
            .statusCode(201)
            .body("FunctionName", equalTo("provided-bootstrap-in-layer"))
            .body("Runtime", equalTo("provided.al2023"))
            .body("State", equalTo("Active"));
    }

    @Test
    void updateFunctionCodeAcceptsProvidedRuntimeWithoutBootstrapInPackage() throws Exception {
        given()
            .contentType("application/json")
            .body(createBody("provided-bootstrap-update", "provided.al2", "bootstrap",
                    zip("bootstrap", "#!/bin/sh\n".getBytes())))
        .when()
            .post("/2015-03-31/functions")
        .then()
            .statusCode(201);

        given()
            .contentType("application/json")
            .body("""
                { "ZipFile": "%s" }
                """.formatted(Base64.getEncoder().encodeToString(zipWithoutBootstrap())))
        .when()
            .put("/2015-03-31/functions/provided-bootstrap-update/code")
        .then()
            .statusCode(200)
            .body("FunctionName", equalTo("provided-bootstrap-update"));
    }
}
