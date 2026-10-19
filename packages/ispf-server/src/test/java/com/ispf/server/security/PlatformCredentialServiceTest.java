package com.ispf.server.security;

import com.ispf.server.persistence.PlatformCredentialRepository;
import com.ispf.server.persistence.entity.PlatformCredentialEntity;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformCredentialServiceTest {

    @Test
    void serializationFailureDoesNotSaveTheSecret() {
        PlatformCredentialRepository repository = mock(PlatformCredentialRepository.class);
        IspfSecretCipher cipher = mock(IspfSecretCipher.class);
        when(repository.findByObjectPath("root.platform.devices.cred")).thenReturn(Optional.empty());
        when(cipher.encrypt("s3cret")).thenReturn("cipher");
        PlatformCredentialService service = new PlatformCredentialService(repository, cipher, new ObjectMapper());

        assertThatThrownBy(() -> service.upsert(
                "root.platform.devices.cred",
                "generic",
                "s3cret",
                brokenMetadata()
        )).isInstanceOf(RuntimeException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void emptyMetadataIsStoredOnlyWhenTheCallerPassedNone() {
        PlatformCredentialRepository repository = mock(PlatformCredentialRepository.class);
        IspfSecretCipher cipher = mock(IspfSecretCipher.class);
        when(repository.findByObjectPath("root.platform.devices.cred")).thenReturn(Optional.empty());
        when(cipher.encrypt("s3cret")).thenReturn("cipher");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformCredentialService service = new PlatformCredentialService(repository, cipher, new ObjectMapper());

        Map<String, Object> empty = service.upsert("root.platform.devices.cred", "generic", "s3cret", null);
        assertThat(empty.get("status")).isEqualTo("OK");

        org.mockito.ArgumentCaptor<PlatformCredentialEntity> saved =
                org.mockito.ArgumentCaptor.forClass(PlatformCredentialEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getMetadataJson()).isEqualTo("{}");
    }

    @Test
    void providedMetadataIsStoredAsJson() {
        PlatformCredentialRepository repository = mock(PlatformCredentialRepository.class);
        IspfSecretCipher cipher = mock(IspfSecretCipher.class);
        when(repository.findByObjectPath("root.platform.devices.cred")).thenReturn(Optional.empty());
        when(cipher.encrypt("s3cret")).thenReturn("cipher");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformCredentialService service = new PlatformCredentialService(repository, cipher, new ObjectMapper());

        service.upsert("root.platform.devices.cred", "generic", "s3cret", Map.of("label", "site"));

        org.mockito.ArgumentCaptor<PlatformCredentialEntity> saved =
                org.mockito.ArgumentCaptor.forClass(PlatformCredentialEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getMetadataJson()).contains("label").contains("site");
    }

    private static Map<String, Object> brokenMetadata() {
        return new AbstractMap<>() {
            @Override
            public Set<Entry<String, Object>> entrySet() {
                throw new IllegalStateException("metadata");
            }
        };
    }
}
