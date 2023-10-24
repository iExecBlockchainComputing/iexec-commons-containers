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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.iexec.commons.containers.DockerRunRequest;
import com.iexec.commons.containers.SgxDriverMode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.util.Collections;
import java.util.List;

@Slf4j
abstract class AbstractDockerTests {

    static final String CHAIN_TASK_ID = "chainTaskId";
    static final String ALPINE_LATEST = "alpine:latest";
    static final String CMD = "cmd";
    static final List<String> ENV = List.of("FOO=bar");
    static final String DOCKER_NETWORK = "dockerTestsNetwork";
    static final String SLASH_IEXEC_IN = File.separator + "iexec_in";
    static final String SLASH_IEXEC_OUT = File.separator + "iexec_out";
    static final String SLASH_TMP = "/tmp";

    @Spy
    DockerClientInstance dockerClientInstance = new DockerClientInstance();
    @Spy
    DockerClientInstance corruptClientInstance = getCorruptInstance();

    @BeforeEach
    void beforeEach(TestInfo testInfo) {
        log.info(">>> {}", testInfo.getDisplayName());
        MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void afterEach(TestInfo testInfo) {
        log.info(">>> {}", testInfo.getDisplayName());
    }

    /**
     * Creates a DockerClient instance trying to communicate with a docker server on a non-existing endpoint.
     * Every single call to this instance will provoke an exception.
     * @return The faulty DockerClient instance
     */
    DockerClientInstance getCorruptInstance() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("tcp://localhost:11111")
                .build();
        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();
        DockerClient corruptDockerClient = DockerClientImpl.getInstance(config, httpClient);
        DockerClientInstance dockerClientInstance = new DockerClientInstance();
        ReflectionTestUtils.setField(dockerClientInstance, "client", corruptDockerClient);
        return dockerClientInstance;
    }

    DockerRunRequest getDefaultDockerRunRequest(SgxDriverMode sgxDriverMode) {
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(Bind.parse(SLASH_IEXEC_IN + ":" + SLASH_IEXEC_OUT))
                .withNetworkMode(DOCKER_NETWORK);
        return DockerRunRequest.builder()
                .hostConfig(hostConfig)
                .containerName(getRandomString())
                .chainTaskId(CHAIN_TASK_ID)
                .imageUri(ALPINE_LATEST)
                .cmd(CMD)
                .env(ENV)
                .sgxDriverMode(sgxDriverMode)
                .containerPort(1000)
                .maxExecutionTime(500000)
                .workingDir(SLASH_TMP)
                .build();
    }

    String getEnvValue(String envVarName) {
        return System.getenv(envVarName) != null ?
                //Intellij envvar injection
                System.getenv(envVarName) :
                //gradle test -DdockerhubPassword=xxx
                System.getProperty(envVarName);
    }

    String getRandomString() {
        return RandomStringUtils.randomAlphanumeric(20);
    }

}
