/*
 * Copyright 2023-2026 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.commons.containers.client;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.AuthCmd;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DockerClientFactoryTests {

    @BeforeEach
    void beforeEach() {
        DockerClientFactory.purgeClients();
    }

    @Test
    void shouldGetTheSameUnauthenticatedClientInstanceWithDefaultRegistry() {
        DockerClientInstance instance1 = DockerClientFactory.getDockerClientInstance();
        DockerClientInstance instance2 = DockerClientFactory.getDockerClientInstance();
        assertThat(instance2).isSameAs(instance1);
    }

    @Test
    void shouldGetTheSameUnauthenticatedClientInstanceWithCustomRegistry() {
        String registryAddress = "registryAddress";
        DockerClientInstance instance1 = DockerClientFactory.getDockerClientInstance(registryAddress);
        DockerClientInstance instance2 = DockerClientFactory.getDockerClientInstance(registryAddress);
        assertThat(instance2).isSameAs(instance1);
    }

    @Test
    void shouldGetTheSameAuthenticatedClient() {
        final String registryAddress = DockerClientInstance.DEFAULT_DOCKER_REGISTRY;
        final String username = "dockerIoUsername";
        final String password = "dockerIoPassword";
        final DockerClient dockerClient = mock(DockerClient.class);
        final AuthCmd authCmd = mock(AuthCmd.class);
        when(dockerClient.authCmd()).thenReturn(authCmd);

        try (final MockedStatic<DockerClientImpl> dockerClientImpl = mockStatic(DockerClientImpl.class)) {
            dockerClientImpl.when(() -> DockerClientImpl.getInstance(any(), any(DockerHttpClient.class)))
                    .thenReturn(dockerClient);

            final DockerClientInstance instance1 = DockerClientFactory.getDockerClientInstance(
                    registryAddress, username, password);
            final DockerClientInstance instance2 = DockerClientFactory.getDockerClientInstance(
                    registryAddress, username, password);
            assertThat(instance2).isSameAs(instance1);
        }
    }
}
