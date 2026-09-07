package io.github.hectorvent.floci.services.apigateway;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code ${stageVariables.name}} references in an integration URI against the
 * variables of the stage serving the request, the way API Gateway does before it dispatches
 * to the integration. An unknown variable resolves to the empty string.
 */
public final class StageVariables {

    private static final Pattern REFERENCE = Pattern.compile("\\$\\{stageVariables\\.([^}]+)}");

    private StageVariables() {
    }

    public static String substitute(String uri, Map<String, String> stageVariables) {
        if (uri == null) {
            return null;
        }
        Matcher matcher = REFERENCE.matcher(uri);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = stageVariables == null ? null : stageVariables.get(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
