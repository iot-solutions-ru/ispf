package com.ispf.server.application.git;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies GitHub / GitLab write order for Push tree → PR (package-private HttpClient seam).
 */
class ApplicationGitRemotePushSequenceTest {

    private HttpClient httpClient;
    private ApplicationGitRemoteClient client;
    private final List<CapturedCall> calls = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        calls.clear();
        httpClient = mock(HttpClient.class);
        client = new ApplicationGitRemoteClient(new ObjectMapper(), httpClient);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    String method = request.method();
                    String uri = request.uri().toString();
                    calls.add(new CapturedCall(method, uri));
                    int status = statusFor(method, uri);
                    HttpResponse<String> response = mock(HttpResponse.class);
                    when(response.statusCode()).thenReturn(status);
                    when(response.body()).thenReturn(bodyFor(method, uri, status));
                    return response;
                });
    }

    @Test
    void githubWriteSequenceUsesBaseShaAndOpensPr() {
        var result = client.pushBundleAndOpenPr(
                "github",
                "https://github.com/example/demo.git",
                "bundle.json",
                "main",
                "ispf/app-demo-push",
                "deadbeefcafebabe",
                "{\n  \"version\" : \"1.0.0\"\n}\n",
                "ispf: push demo",
                "ispf: demo",
                "body",
                "ghp_test"
        );

        assertEquals("ispf/app-demo-push", result.branch());
        assertEquals(4, calls.size());
        assertEquals("POST", calls.get(0).method());
        assertTrue(calls.get(0).uri().endsWith("/repos/example/demo/git/refs"));
        assertEquals("GET", calls.get(1).method());
        assertTrue(calls.get(1).uri().contains("/contents/bundle.json"));
        assertEquals("PUT", calls.get(2).method());
        assertTrue(calls.get(2).uri().endsWith("/repos/example/demo/contents/bundle.json"));
        assertEquals("POST", calls.get(3).method());
        assertTrue(calls.get(3).uri().endsWith("/repos/example/demo/pulls"));
        assertEquals("abc111", result.commitSha());
        assertEquals(7, result.prNumber());
    }

    @Test
    void gitlabWriteSequenceCreatesBranchFileAndMr() {
        var result = client.pushBundleAndOpenPr(
                "gitlab",
                "https://gitlab.com/group/demo.git",
                "bundle.json",
                "main",
                "ispf/app-demo-push",
                "deadbeefcafebabe",
                "{\n  \"version\" : \"1.0.0\"\n}\n",
                "ispf: push demo",
                "ispf: demo",
                "body",
                "glpat-test"
        );

        assertEquals(4, calls.size());
        assertEquals("POST", calls.get(0).method());
        assertTrue(calls.get(0).uri().contains("/repository/branches"));
        assertEquals("GET", calls.get(1).method());
        assertTrue(calls.get(1).uri().contains("/repository/files/"));
        assertEquals("POST", calls.get(2).method());
        assertTrue(calls.get(2).uri().contains("/repository/files/"));
        assertEquals("POST", calls.get(3).method());
        assertTrue(calls.get(3).uri().contains("/merge_requests"));
        assertEquals("gl-sha-1", result.commitSha());
        assertEquals(9, result.prNumber());
    }

    private static int statusFor(String method, String uri) {
        if (("GET".equals(method) && uri.contains("/contents/bundle.json?"))
                || ("GET".equals(method) && uri.contains("/repository/files/"))) {
            return 404;
        }
        return 200;
    }

    private static String bodyFor(String method, String uri, int status) {
        if (status == 404) {
            return "{\"message\":\"Not Found\"}";
        }
        if (uri.contains("/contents/bundle.json") && "PUT".equals(method)) {
            return "{\"commit\":{\"sha\":\"abc111\"}}";
        }
        if (uri.endsWith("/pulls") && "POST".equals(method)) {
            return "{\"html_url\":\"https://github.com/example/demo/pull/7\",\"number\":7}";
        }
        if (uri.contains("/repository/files/") && ("POST".equals(method) || "PUT".equals(method))) {
            return "{\"commit_id\":\"gl-sha-1\"}";
        }
        if (uri.contains("/merge_requests") && "POST".equals(method)) {
            return "{\"web_url\":\"https://gitlab.com/group/demo/-/merge_requests/9\",\"iid\":9}";
        }
        return "{}";
    }

    private record CapturedCall(String method, String uri) {
    }
}
