/*
 * Copyright 2023 IEXEC BLOCKCHAIN TECH
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DockerClientFactoryTests {

    private static final String DOCKER_IO_USER = "DOCKER_IO_USER";
    private static final String DOCKER_IO_PASSWORD = "DOCKER_IO_PASSWORD";

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
        String dockerIoUsername = getEnvValue(DOCKER_IO_USER);
        String dockerIoPassword = getEnvValue(DOCKER_IO_PASSWORD);
        DockerClientInstance instance1 = DockerClientFactory.getDockerClientInstance(
                DockerClientInstance.DEFAULT_DOCKER_REGISTRY, dockerIoUsername, dockerIoPassword);
        DockerClientInstance instance2 = DockerClientFactory.getDockerClientInstance(
                DockerClientInstance.DEFAULT_DOCKER_REGISTRY, dockerIoUsername, dockerIoPassword);
        assertThat(instance2).isSameAs(instance1);
    }

    private String getEnvValue(String envVarName) {
        return System.getenv(envVarName) != null ?
                //Intellij envvar injection
                System.getenv(envVarName) :
                //gradle test -DdockerhubPassword=xxx
                System.getProperty(envVarName);
    }
}
