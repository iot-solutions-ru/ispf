package com.ispf.driver.opcua;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.client.security.DefaultClientCertificateValidator;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

final class OpcUaClients {

    private static final String APPLICATION_URI = "urn:ispf:driver:opcua";

    private OpcUaClients() {
    }

    static OpcUaClient create(
            String endpointUrl,
            OpcUaSecuritySettings security,
            OpcUaClientPki pki,
            String applicationName
    ) throws Exception {
        return OpcUaClient.create(
                endpointUrl,
                endpoints -> {
                    var match = selectEndpoint(endpoints, security);
                    if (match.isEmpty()) {
                        throw new IllegalStateException(
                                "No OPC UA endpoint for " + security.policy().name()
                                        + " / " + security.mode()
                                        + ". Available: " + describeEndpoints(endpoints)
                        );
                    }
                    return match;
                },
                configBuilder -> {
                    configBuilder
                            .setApplicationName(LocalizedText.english(applicationName))
                            .setApplicationUri(APPLICATION_URI);
                    if (security.secure()) {
                        if (pki == null) {
                            throw new IllegalStateException("PKI required for " + security.policy().name());
                        }
                        configBuilder
                                .setKeyPair(pki.keyPair())
                                .setCertificate(pki.certificate())
                                .setCertificateChain(new X509Certificate[]{pki.certificate()})
                                .setCertificateValidator(new DefaultClientCertificateValidator(pki.trustList()));
                    }
                    return configBuilder.build();
                }
        );
    }

    static Optional<EndpointDescription> selectEndpoint(
            List<EndpointDescription> endpoints,
            OpcUaSecuritySettings security
    ) {
        return endpoints.stream()
                .filter(endpoint -> security.policy().getUri().equals(endpoint.getSecurityPolicyUri()))
                .filter(endpoint -> security.mode() == endpoint.getSecurityMode())
                .findFirst();
    }

    static String describeEndpoints(List<EndpointDescription> endpoints) {
        return endpoints.stream()
                .map(endpoint -> endpoint.getSecurityPolicyUri() + " / " + endpoint.getSecurityMode())
                .collect(Collectors.joining(", "));
    }
}
