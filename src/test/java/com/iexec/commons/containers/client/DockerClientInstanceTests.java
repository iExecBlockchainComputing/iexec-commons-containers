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
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.DockerException;
import com.iexec.commons.containers.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Spy;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(OutputCaptureExtension.class)
class DockerClientInstanceTests extends AbstractDockerTests {

    //classic
    private static final String DOCKER_IO_CLASSIC_IMAGE = "docker.io/alpine/socat:latest";
    private static final String SHORT_CLASSIC_IMAGE = "alpine/socat:latest";
    //library
    private static final String DOCKER_IO_LIBRARY_IMAGE = "docker.io/library/alpine:latest";
    private static final String SHORT_LIBRARY_IMAGE = "library/alpine:latest";
    private static final String VERY_SHORT_LIBRARY_IMAGE = "alpine:latest";
    // deprecated
    private static final String DOCKER_COM_CLASSIC_IMAGE = "registry.hub.docker.com/alpine/socat:latest";
    // other
    private static final String ALPINE_BLABLA = "alpine:blabla";
    private static final String BLABLA_LATEST = "blabla:latest";
    private static final String DOCKERHUB_USERNAME_ENV_NAME = "DOCKER_IO_USER";
    private static final String DOCKERHUB_PASSWORD_ENV_NAME = "DOCKER_IO_PASSWORD";
    private static final String PRIVATE_IMAGE_NAME = "iexechub/private-image:alpine-3.13";
    private static final String DEVICE_PATH_IN_CONTAINER = "/dev/some-device-in-container";
    private static final String DEVICE_PATH_ON_HOST = "/dev/some-device-on-host";

    private static final List<String> usedRandomNames = new ArrayList<>();
    private static final List<String> usedImages = List.of(
            DOCKER_IO_CLASSIC_IMAGE, SHORT_CLASSIC_IMAGE, DOCKER_IO_LIBRARY_IMAGE,
            SHORT_LIBRARY_IMAGE, VERY_SHORT_LIBRARY_IMAGE, DOCKER_COM_CLASSIC_IMAGE,
            ALPINE_LATEST);

    @Spy
    private DockerClient spiedClient = dockerClientInstance.getClient();

    @BeforeAll
    static void beforeAll() {
        usedImages.forEach(imageName -> new DockerClientInstance().pullImage(imageName));
    }

    @AfterAll
    static void afterAll() {
        DockerClientInstance instance = new DockerClientInstance();
        // clean containers
        usedRandomNames.forEach(instance::stopAndRemoveContainer);
        // clean networks
        usedRandomNames.forEach(instance::removeNetwork);
        instance.removeNetwork(DOCKER_NETWORK);
        // clean docker images
        usedImages.forEach(instance::removeImage);
    }

    //region DockerClientInstance
    @Test
    void shouldCreateUnauthenticatedClientWithDefaultRegistry() {
        DockerClientInstance instance = new DockerClientInstance();
        assertThat(instance.getClient().authConfig().getRegistryAddress())
                .isEqualTo(DockerClientInstance.DEFAULT_DOCKER_REGISTRY);
        assertThat(instance.getClient().authConfig().getPassword()).isNull();
    }

    @Test
    void shouldCreateUnauthenticatedClientWithCustomRegistry() {
        String registryAddress = "registryAddress";
        DockerClientInstance instance = new DockerClientInstance(registryAddress);
        assertThat(instance.getClient().authConfig().getRegistryAddress())
                .isEqualTo(registryAddress);
        assertThat(instance.getClient().authConfig().getPassword()).isNull();
    }

    @Test
    void shouldGetAuthenticatedClientWithDockerIoRegistry() {
        String dockerIoUsername = getEnvValue(DOCKERHUB_USERNAME_ENV_NAME);
        String dockerIoPassword = getEnvValue(DOCKERHUB_PASSWORD_ENV_NAME);
        DockerClientInstance instance = new DockerClientInstance(
                DockerClientInstance.DEFAULT_DOCKER_REGISTRY,
                dockerIoUsername, dockerIoPassword);
        assertThat(instance.getClient().authConfig().getRegistryAddress())
                .isEqualTo(DockerClientInstance.DEFAULT_DOCKER_REGISTRY);
        assertThat(instance.getClient().authConfig().getUsername())
                .isEqualTo(dockerIoUsername);
        assertThat(instance.getClient().authConfig().getPassword())
                .isEqualTo(dockerIoPassword);
    }
    //endregion

    /**
     * This test is temporarily disabled because of this error:
     * toomanyrequests: too many failed login attempts for
     * username or IP address.
     */
    @Test
    @Disabled("toomanyrequests: too many failed login attempts for username or IP address")
    void shouldFailToAuthenticateClientToRegistry() {
        DockerException e = assertThrows(DockerException.class, () -> DockerClientFactory
                .getDockerClientInstance(DockerClientInstance.DEFAULT_DOCKER_REGISTRY, "badUsername", "badPassword"));
        assertThat(e.getHttpStatus()).isEqualTo(401);
    }

    //region isImagePresent
    @Test
    void shouldFindImagePresent() {
        assertThat(dockerClientInstance.isImagePresent(ALPINE_LATEST)).isTrue();
    }

    @Test
    void shouldNotFindImagePresent() {
        assertThat(dockerClientInstance.isImagePresent(getRandomString())).isFalse();
    }

    @Test
    void shouldNotFindImagePresentSinceEmptyName() {
        assertThat(dockerClientInstance.isImagePresent("")).isFalse();
    }


    @Test
    void shouldNotFindImagePresentSinceDockerCmdException() {
        assertThat(corruptClientInstance.isImagePresent("")).isFalse();
    }
    //endregion

    //region pullImage
    @Test
    void shouldPullImage() {
        dockerClientInstance.removeImage(ALPINE_LATEST);
        assertThat(dockerClientInstance.pullImage(ALPINE_LATEST)).isTrue();
        assertThat(dockerClientInstance.isImagePresent(ALPINE_LATEST)).isTrue();
    }

    @Test
    void shouldPullImageWithExplicitTimeout() {
        dockerClientInstance.removeImage(ALPINE_LATEST);
        assertThat(dockerClientInstance.pullImage(ALPINE_LATEST, Duration.of(3, ChronoUnit.MINUTES))).isTrue();
        assertThat(dockerClientInstance.isImagePresent(ALPINE_LATEST)).isTrue();
    }

    @Test
    void shouldNotPullImageSinceTimeout() {
        dockerClientInstance.removeImage(ALPINE_LATEST);
        assertThat(dockerClientInstance.pullImage(ALPINE_LATEST, Duration.of(1, ChronoUnit.SECONDS))).isFalse();
        assertThat(dockerClientInstance.isImagePresent(ALPINE_LATEST)).isFalse();
        dockerClientInstance.pullImage(ALPINE_LATEST);
    }

    @Test
    void shouldNotPullImageSinceNoTag() {
        assertThat(dockerClientInstance.pullImage("alpine")).isFalse();
    }

    @Test
    void shouldNotPullImageSinceEmptyImageName() {
        assertThat(dockerClientInstance.pullImage("")).isFalse();
    }

    @Test
    void shouldNotPullImageSinceEmptyNameButPresentTag() {
        assertThat(dockerClientInstance.pullImage(":latest")).isFalse();
    }

    @Test
    void shouldNotPullImageSincePresentNameButEmptyTag() {
        assertThat(dockerClientInstance.pullImage("blabla:")).isFalse();
    }

    @Test
    void shouldNotPullImageSinceWrongName() {
        assertThat(dockerClientInstance.pullImage(BLABLA_LATEST)).isFalse();
    }

    @Test
    void shouldNotPullImageSinceWrongTag() {
        assertThat(dockerClientInstance.pullImage(ALPINE_BLABLA)).isFalse();
    }

    @Test
    void shouldNotPullImageSinceDockerCmdException() {
        assertThat(corruptClientInstance.pullImage(getRandomString())).isFalse();
    }

    @Test
    void shouldNotPullImageSinceInterruptedException(CapturedOutput stdout) throws InterruptedException {
        final DockerClient dockerClient = mock(DockerClient.class);
        final PullImageCmd pullImageCmd = mock(PullImageCmd.class);
        final PullImageResultCallback resultCallback = mock(PullImageResultCallback.class);
        ReflectionTestUtils.setField(dockerClientInstance, "client", dockerClient);
        when(dockerClient.pullImageCmd("alpine")).thenReturn(pullImageCmd);
        when(pullImageCmd.withTag("latest")).thenReturn(pullImageCmd);
        when(pullImageCmd.exec(any())).thenReturn(resultCallback);
        when(resultCallback.awaitCompletion(60, TimeUnit.SECONDS)).thenThrow(InterruptedException.class);
        dockerClientInstance.pullImage(ALPINE_LATEST);
        assertThat(stdout.getOut()).contains("Docker pull command was interrupted");
    }

    /**
     * Try to pull a private image from iexechub, require valid login and permissions.
     * The test will fail if Docker Hub credentials are missing or invalid.
     */
    @Test
    void shouldPullPrivateImage() {
        String username = getEnvValue(DOCKERHUB_USERNAME_ENV_NAME);
        String password = getEnvValue(DOCKERHUB_PASSWORD_ENV_NAME);
        // Get an authenticated docker client
        DockerClientInstance authClientInstance =
                new DockerClientInstance(DockerClientInstance.DEFAULT_DOCKER_REGISTRY,
                        username, password);
        // clean to avoid previous tests collisions
        authClientInstance.removeImage(PRIVATE_IMAGE_NAME);
        // pull image and check
        assertThat(authClientInstance.pullImage(PRIVATE_IMAGE_NAME)).isTrue();
        // clean
        authClientInstance.removeImage(PRIVATE_IMAGE_NAME);
    }
    //endregion

    //region getImageId
    @Test
    void shouldGetImageId() {
        dockerClientInstance.pullImage(ALPINE_LATEST);
        assertThat(dockerClientInstance.getImageId(ALPINE_LATEST)).isNotEmpty();
    }

    @Test
    void shouldGetImageIdWithDockerIoClassicImage() {
        String image = DOCKER_IO_CLASSIC_IMAGE;
        dockerClientInstance.pullImage(image);
        assertThat(dockerClientInstance.getImageId(image)).isNotEmpty();
    }

    @Test
    void shouldGetImageIdWithShortClassicImage() {
        String image = SHORT_CLASSIC_IMAGE;
        dockerClientInstance.pullImage(image);
        assertThat(dockerClientInstance.getImageId(image)).isNotEmpty();
    }

    @Test
    void shouldGetImageIdWithDockerIoLibraryImage() {
        String image = DOCKER_IO_LIBRARY_IMAGE;
        dockerClientInstance.pullImage(image);
        assertThat(dockerClientInstance.getImageId(image)).isNotEmpty();
    }

    @Test
    void shouldGetImageIdWithShortLibraryImage() {
        String image = SHORT_LIBRARY_IMAGE;
        dockerClientInstance.pullImage(image);
        assertThat(dockerClientInstance.getImageId(image)).isNotEmpty();
    }

    @Test
    void shouldGetImageIdWithVeryShortLibraryImage() {
        String image = VERY_SHORT_LIBRARY_IMAGE;
        dockerClientInstance.pullImage(image);
        assertThat(dockerClientInstance.getImageId(image)).isNotEmpty();
    }

    @Test
    void shouldGetImageIdWithClassicDockerComImage() {
        String image = DOCKER_COM_CLASSIC_IMAGE;
        dockerClientInstance.pullImage(image);
        String imageId = dockerClientInstance.getImageId(image);
        assertThat(imageId).isNotEmpty();
    }

    @Test
    void shouldNotGetImageIdSinceEmptyName() {
        assertThat(dockerClientInstance.getImageId("")).isEmpty();
    }

    @Test
    void shouldNotGetImageId() {
        assertThat(dockerClientInstance.getImageId(BLABLA_LATEST)).isEmpty();
    }

    @Test
    void shouldNotGetImageIdSinceDockerCmdException() {
        assertThat(corruptClientInstance.getImageId(getRandomString())).isEmpty();
    }
    //endregion

    //region sanitizeImageName
    @Test
    void shouldGetSanitizedImageWithDockerIoClassicImage() {
        assertThat(dockerClientInstance.sanitizeImageName(DOCKER_IO_CLASSIC_IMAGE))
                .isEqualTo("alpine/socat:latest");
    }

    @Test
    void shouldGetSanitizedImageWithDockerIoLibraryImage() {
        assertThat(dockerClientInstance.sanitizeImageName(DOCKER_IO_LIBRARY_IMAGE))
                .isEqualTo("alpine:latest");
    }

    @Test
    void shouldGetSanitizedImageWithShortLibraryImage() {
        assertThat(dockerClientInstance.sanitizeImageName(SHORT_LIBRARY_IMAGE))
                .isEqualTo("alpine:latest");
    }

    @Test
    void shouldGetSanitizedImageIfShortNameLibraryName() {
        assertThat(dockerClientInstance.sanitizeImageName(VERY_SHORT_LIBRARY_IMAGE))
                .isEqualTo("alpine:latest");
    }

    @Test
    void shouldDoNothingTOSanitizeImageWithDockerComClassicImage() {
        assertThat(dockerClientInstance.sanitizeImageName(DOCKER_COM_CLASSIC_IMAGE))
                .isEqualTo(DOCKER_COM_CLASSIC_IMAGE);
    }

    @Test
    void shouldDoNothingForSanitizedImage() {
        String image = "nexus.iex.ec/some-app:latest";
        assertThat(dockerClientInstance.sanitizeImageName(image))
                .isEqualTo(image);
    }
    //endregion

    //region removeImage
    @Test
    void shouldRemoveImage() {
        dockerClientInstance.pullImage(DOCKER_IO_CLASSIC_IMAGE);
        assertThat(dockerClientInstance.removeImage(DOCKER_IO_CLASSIC_IMAGE)).isTrue();
    }

    @Test
    void shouldNotRemoveImageByIdSinceEmptyName() {
        assertThat(dockerClientInstance.removeImage("")).isFalse();
    }

    @Test
    void shouldNotRemoveImageByIdSinceDockerCmdException() {
        assertThat(corruptClientInstance.removeImage(ALPINE_LATEST)).isFalse();
    }
    //endregion

    //region run
    @Test
    void shouldRunSuccessfullyAndWaitForContainerToFinish() throws TimeoutException {
        DockerRunRequest dockerRunRequest = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        dockerRunRequest.setMaxExecutionTime(5000); // 5s
        String msg = "Hello world!";
        dockerRunRequest.setCmd("sh -c 'sleep 2 && echo " + msg + "'");
        String containerName = dockerRunRequest.getContainerName();

        DockerRunResponse dockerRunResponse =
                dockerClientInstance.run(dockerRunRequest);

        assertThat(dockerRunResponse).isNotNull();
        assertThat(dockerRunResponse.getFinalStatus()).isEqualTo(DockerRunFinalStatus.SUCCESS);
        assertThat(dockerRunResponse.getContainerExitCode()).isZero();
        assertThat(dockerRunResponse.getStdout().trim()).isEqualTo(msg);
        assertThat(dockerRunResponse.getStderr()).isEmpty();
        assertThat(dockerRunResponse.getExecutionDuration()).isGreaterThan(Duration.ofSeconds(2));
        verify(dockerClientInstance).createContainer(dockerRunRequest);
        verify(dockerClientInstance).startContainer(containerName);
        verify(dockerClientInstance)
                .waitContainerUntilExitOrTimeout(eq(containerName), any());
        verify(dockerClientInstance, never()).stopContainer(containerName);
        verify(dockerClientInstance).getContainerLogs(containerName);
        verify(dockerClientInstance).removeContainer(containerName);
    }

    @Test
    void shouldRunSuccessfullyAndNotWaitForTimeout() throws TimeoutException {
        DockerRunRequest dockerRunRequest = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        dockerRunRequest.setMaxExecutionTime(0); // detached mode // can be -1
        dockerRunRequest.setCmd("sh -c 'sleep 30'");
        String containerName = dockerRunRequest.getContainerName();

        DockerRunResponse dockerRunResponse =
                dockerClientInstance.run(dockerRunRequest);

        assertThat(dockerRunResponse).isNotNull();
        assertThat(dockerRunResponse.getFinalStatus()).isEqualTo(DockerRunFinalStatus.SUCCESS);
        assertThat(dockerRunResponse.getContainerExitCode()).isEqualTo(-1);
        assertThat(dockerRunResponse.getStdout()).isEmpty();
        assertThat(dockerRunResponse.getStderr()).isEmpty();
        assertThat(dockerRunResponse.getExecutionDuration()).isNull();
        verify(dockerClientInstance).createContainer(dockerRunRequest);
        verify(dockerClientInstance).startContainer(containerName);
        verify(dockerClientInstance, never())
                .waitContainerUntilExitOrTimeout(eq(containerName), any());
        verify(dockerClientInstance, never()).stopContainer(containerName);
        verify(dockerClientInstance, never()).getContainerLogs(containerName);
        verify(dockerClientInstance, never()).removeContainer(containerName);
        // clean
        dockerClientInstance.stopAndRemoveContainer(containerName);
    }

    @Test
    void shouldRunAndReturnFailureInStderrSinceBadCmd() throws TimeoutException {
        DockerRunRequest dockerRunRequest = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        dockerRunRequest.setMaxExecutionTime(5000); // 5s
        dockerRunRequest.setCmd("sh -c 'someBadCmd'");
        String containerName = dockerRunRequest.getContainerName();

        DockerRunResponse dockerRunResponse =
                dockerClientInstance.run(dockerRunRequest);

        System.out.println(dockerRunResponse);
        assertThat(dockerRunResponse).isNotNull();
        assertThat(dockerRunResponse.getFinalStatus()).isEqualTo(DockerRunFinalStatus.FAILED);
        assertThat(dockerRunResponse.getContainerExitCode()).isNotZero();
        assertThat(dockerRunResponse.getStdout()).isEmpty();
        assertThat(dockerRunResponse.getStderr()).isNotEmpty();
        assertThat(dockerRunResponse.getExecutionDuration()).isGreaterThanOrEqualTo(Duration.ZERO);
        verify(dockerClientInstance).createContainer(dockerRunRequest);
        verify(dockerClientInstance).startContainer(containerName);
        verify(dockerClientInstance)
                .waitContainerUntilExitOrTimeout(eq(containerName), any());
        verify(dockerClientInstance, never()).stopContainer(containerName);
        verify(dockerClientInstance).getContainerLogs(containerName);
        verify(dockerClientInstance).removeContainer(containerName);
    }

    @Test
    void shouldRunAndReturnFailureAndLogsSinceTimeout() throws TimeoutException {
        DockerRunRequest dockerRunRequest = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        dockerRunRequest.setMaxExecutionTime(5000); // 5s
        String msg1 = "First message";
        String msg2 = "Second message";
        String cmd = String.format("sh -c 'echo %s && sleep 10 && echo %s'", msg1, msg2);
        dockerRunRequest.setCmd(cmd);
        String containerName = dockerRunRequest.getContainerName();

        DockerRunResponse dockerRunResponse =
                dockerClientInstance.run(dockerRunRequest);

        System.out.println(dockerRunResponse);
        assertThat(dockerRunResponse).isNotNull();
        assertThat(dockerRunResponse.getFinalStatus()).isEqualTo(DockerRunFinalStatus.TIMEOUT);
        assertThat(dockerRunResponse.getContainerExitCode()).isEqualTo(-1);
        assertThat(dockerRunResponse.getStdout().trim()).isEqualTo(msg1);
        assertThat(dockerRunResponse.getStderr()).isEmpty();
        assertThat(dockerRunResponse.getExecutionDuration())
                .isGreaterThanOrEqualTo(Duration.ofSeconds(5))
                .isLessThan(Duration.ofSeconds(6));
        verify(dockerClientInstance).createContainer(dockerRunRequest);
        verify(dockerClientInstance).startContainer(containerName);
        verify(dockerClientInstance)
                .waitContainerUntilExitOrTimeout(eq(containerName), any());
        verify(dockerClientInstance).stopContainer(containerName);
        verify(dockerClientInstance).getContainerLogs(containerName);
        verify(dockerClientInstance).removeContainer(containerName);
    }

    @Test
    void shouldReturnFailureSinceCantCreateContainer() throws TimeoutException {
        DockerRunRequest dockerRunRequest = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        dockerRunRequest.setMaxExecutionTime(5000); // 5s
        String msg = "Hello world!";
        dockerRunRequest.setCmd("sh -c 'sleep 2 && echo " + msg + "'");
        String containerName = dockerRunRequest.getContainerName();
        doReturn("").when(dockerClientInstance).createContainer(dockerRunRequest);

        DockerRunResponse dockerRunResponse =
                dockerClientInstance.run(dockerRunRequest);

        System.out.println(dockerRunResponse);
        assertThat(dockerRunResponse).isNotNull();
        assertThat(dockerRunResponse.getFinalStatus()).isEqualTo(DockerRunFinalStatus.FAILED);
        assertThat(dockerRunResponse.getContainerExitCode()).isEqualTo(-1);
        assertThat(dockerRunResponse.getStdout()).isEmpty();
        assertThat(dockerRunResponse.getStderr()).isEmpty();
        assertThat(dockerRunResponse.getExecutionDuration()).isNull();
        verify(dockerClientInstance).createContainer(dockerRunRequest);
        verify(dockerClientInstance, never()).startContainer(containerName);
        verify(dockerClientInstance, never())
                .waitContainerUntilExitOrTimeout(eq(containerName), any());
        verify(dockerClientInstance, never()).stopContainer(containerName);
        verify(dockerClientInstance, never()).getContainerLogs(containerName);
        verify(dockerClientInstance, never()).removeContainer(containerName);
    }

    @Test
    void shouldReturnFailureSinceCantStartContainer() throws TimeoutException {
        DockerRunRequest dockerRunRequest = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        dockerRunRequest.setMaxExecutionTime(5000); // 5s
        String msg = "Hello world!";
        dockerRunRequest.setCmd("sh -c 'sleep 2 && echo " + msg + "'");
        String containerName = dockerRunRequest.getContainerName();
        doReturn(false).when(dockerClientInstance).startContainer(containerName);

        DockerRunResponse dockerRunResponse =
                dockerClientInstance.run(dockerRunRequest);

        System.out.println(dockerRunResponse);
        assertThat(dockerRunResponse).isNotNull();
        assertThat(dockerRunResponse.getFinalStatus()).isEqualTo(DockerRunFinalStatus.FAILED);
        assertThat(dockerRunResponse.getContainerExitCode()).isEqualTo(-1);
        assertThat(dockerRunResponse.getStdout()).isEmpty();
        assertThat(dockerRunResponse.getStderr()).isEmpty();
        assertThat(dockerRunResponse.getExecutionDuration()).isNull();
        verify(dockerClientInstance).createContainer(dockerRunRequest);
        verify(dockerClientInstance).startContainer(containerName);
        verify(dockerClientInstance, never())
                .waitContainerUntilExitOrTimeout(eq(containerName), any());
        verify(dockerClientInstance, never()).stopContainer(containerName);
        verify(dockerClientInstance, never()).getContainerLogs(containerName);
        verify(dockerClientInstance).removeContainer(containerName);
    }

    @Test
    void shouldReturnFailureSinceCantStopContainer() throws TimeoutException {
        DockerRunRequest dockerRunRequest = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        dockerRunRequest.setMaxExecutionTime(5000); // 5s
        String msg = "Hello world!";
        dockerRunRequest.setCmd("sh -c 'sleep 10 && echo " + msg + "'");
        String containerName = dockerRunRequest.getContainerName();
        doReturn(false).when(dockerClientInstance).stopContainer(containerName);

        DockerRunResponse dockerRunResponse =
                dockerClientInstance.run(dockerRunRequest);

        System.out.println(dockerRunResponse);
        assertThat(dockerRunResponse).isNotNull();
        assertThat(dockerRunResponse.getFinalStatus()).isEqualTo(DockerRunFinalStatus.TIMEOUT);
        assertThat(dockerRunResponse.getContainerExitCode()).isEqualTo(-1);
        assertThat(dockerRunResponse.getStdout()).isEmpty();
        assertThat(dockerRunResponse.getStderr()).isEmpty();
        assertThat(dockerRunResponse.getExecutionDuration()).isNull();
        verify(dockerClientInstance).createContainer(dockerRunRequest);
        verify(dockerClientInstance).startContainer(containerName);
        verify(dockerClientInstance)
                .waitContainerUntilExitOrTimeout(eq(containerName), any());
        verify(dockerClientInstance).stopContainer(containerName);
        verify(dockerClientInstance).getContainerLogs(containerName);
        verify(dockerClientInstance, never()).removeContainer(containerName);
        // clean
        doCallRealMethod().when(dockerClientInstance).stopContainer(containerName);
        dockerClientInstance.stopAndRemoveContainer(containerName);
    }

    @Test
    void shouldReturnSuccessButLogsSinceCantRemoveContainer() throws TimeoutException {
        DockerRunRequest dockerRunRequest = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        dockerRunRequest.setMaxExecutionTime(5000); // 5s
        String msg = "Hello world!";
        dockerRunRequest.setCmd("sh -c 'sleep 2 && echo " + msg + "'");
        String containerName = dockerRunRequest.getContainerName();
        doReturn(false).when(dockerClientInstance).removeContainer(containerName);

        DockerRunResponse dockerRunResponse =
                dockerClientInstance.run(dockerRunRequest);

        System.out.println(dockerRunResponse);
        assertThat(dockerRunResponse).isNotNull();
        assertThat(dockerRunResponse.getFinalStatus()).isEqualTo(DockerRunFinalStatus.SUCCESS);
        assertThat(dockerRunResponse.getContainerExitCode()).isZero();
        assertThat(dockerRunResponse.getStdout().trim()).isEqualTo(msg);
        assertThat(dockerRunResponse.getStderr()).isEmpty();
        assertThat(dockerRunResponse.getExecutionDuration()).isGreaterThan(Duration.ofSeconds(2));
        verify(dockerClientInstance).createContainer(dockerRunRequest);
        verify(dockerClientInstance).startContainer(containerName);
        verify(dockerClientInstance)
                .waitContainerUntilExitOrTimeout(eq(containerName), any());
        verify(dockerClientInstance, never()).stopContainer(containerName);
        verify(dockerClientInstance).getContainerLogs(containerName);
        verify(dockerClientInstance).removeContainer(containerName);
        // clean
        doCallRealMethod().when(dockerClientInstance).removeContainer(containerName);
        dockerClientInstance.stopAndRemoveContainer(containerName);
    }
    //endregion

    //region createContainer
    @Test
    void shouldCreateContainer() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        String containerId = dockerClientInstance.createContainer(request);
        assertThat(containerId).isNotEmpty();
        // cleaning
        dockerClientInstance.removeContainer(request.getContainerName());
    }

    @Test
    void shouldNotCreateContainerSinceNoRequest() {
        assertThat(dockerClientInstance.createContainer(null)).isEmpty();
    }

    @Test
    void shouldNotCreateContainerSinceEmptyContainerName() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        request.setContainerName("");
        assertThat(dockerClientInstance.createContainer(request)).isEmpty();
    }

    @Test
    void shouldNotCreateContainerSinceEmptyImageUri() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        request.setImageUri("");
        assertThat(dockerClientInstance.createContainer(request)).isEmpty();
    }

    @Test
    void shouldNotCreateContainerSinceDockerCmdException() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        when(corruptClientInstance.isContainerPresent(request.getContainerName())).thenReturn(false);
        when(corruptClientInstance.createNetwork(DOCKER_NETWORK)).thenReturn("networkId");
        assertThat(corruptClientInstance.createContainer(request)).isEmpty();
    }

    @Test
    void shouldCreateContainerAndRemoveExistingDuplicate() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        // create first container
        String container1Id = dockerClientInstance.createContainer(request);
        // create second container with same name (should replace previous one)
        String container2Id = dockerClientInstance.createContainer(request);
        assertThat(container2Id)
                .isNotEmpty()
                .isNotEqualTo(container1Id);
        // cleaning
        dockerClientInstance.removeContainer(request.getContainerName());
    }

    @Test
    void shouldNotCreateContainerSinceDuplicateIsPresent() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        // create first container
        String container1Id = dockerClientInstance.createContainer(request);
        // create second container with same name (should not replace previous one)
        String container2Id = dockerClientInstance.createContainer(request, false);
        assertThat(container1Id).isNotEmpty();
        assertThat(container2Id).isEmpty();
        // cleaning
        dockerClientInstance.removeContainer(request.getContainerName());
    }
    //endregion

    //region createContainerCmd
    @Test
    void shouldBuildCreateContainerCmdFromRunRequest() {
        CreateContainerCmd createContainerCmd = dockerClientInstance.getClient()
                .createContainerCmd("repo/image:tag");
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        request.setCmd("");
        request.setEnv(null);
        request.setContainerPort(0);

        Optional<CreateContainerCmd> oActualCreateContainerCmd =
                dockerClientInstance.buildCreateContainerCmdFromRunRequest(request,
                        createContainerCmd);
        assertThat(oActualCreateContainerCmd).isPresent();
        CreateContainerCmd actualCreateContainerCmd = oActualCreateContainerCmd.get();
        assertThat(actualCreateContainerCmd.getName())
                .isEqualTo(request.getContainerName());
        assertThat(actualCreateContainerCmd.getCmd()).isNull();
        assertThat(actualCreateContainerCmd.getEnv()).isNull();
        assertThat(actualCreateContainerCmd.getExposedPorts()).isEmpty();
    }

    @Test
    void shouldBuildCreateContainerCmdFromRunRequestWithFullParams() {
        CreateContainerCmd createContainerCmd = dockerClientInstance.getClient()
                .createContainerCmd("repo/image:tag");
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);

        Optional<CreateContainerCmd> oActualCreateContainerCmd =
                dockerClientInstance.buildCreateContainerCmdFromRunRequest(request,
                        createContainerCmd);
        assertThat(oActualCreateContainerCmd).isPresent();
        CreateContainerCmd actualCreateContainerCmd = oActualCreateContainerCmd.get();
        assertThat(actualCreateContainerCmd.getName())
                .isEqualTo(request.getContainerName());
        assertThat(actualCreateContainerCmd.getCmd())
                .isEqualTo(ArgsUtils.stringArgsToArrayArgs(request.getCmd()));
        assertThat(actualCreateContainerCmd.getEnv()).isNotNull();
        assertThat(Arrays.asList(actualCreateContainerCmd.getEnv()))
                .isEqualTo(request.getEnv());
        assertThat(actualCreateContainerCmd.getExposedPorts()).isNotNull();
        assertThat(actualCreateContainerCmd.getExposedPorts()[0].getPort())
                .isEqualTo(1000);
        assertThat(actualCreateContainerCmd.getWorkingDir()).isEqualTo(SLASH_TMP);
    }

    @Test
    void shouldNotBuildCreateContainerCmdFromRunRequestSinceNoRequest() {
        Optional<CreateContainerCmd> actualCreateContainerCmd =
                dockerClientInstance.buildCreateContainerCmdFromRunRequest(
                        getDefaultDockerRunRequest(SgxDriverMode.NONE),
                        null);
        assertThat(actualCreateContainerCmd).isEmpty();
    }

    @Test
    void shouldNotBuildCreateContainerCmdFromRunRequestSinceNoCreateContainerCmd() {
        Optional<CreateContainerCmd> actualCreateContainerCmd =
                dockerClientInstance.buildCreateContainerCmdFromRunRequest(
                        null,
                        dockerClientInstance.getClient()
                                .createContainerCmd("repo/image:tag")
                );
        assertThat(actualCreateContainerCmd).isEmpty();
    }
    //endregion

    //region isContainerPresent
    @Test
    void shouldIsContainerPresentBeTrue() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        dockerClientInstance.createContainer(request);

        boolean isPresent = dockerClientInstance
                .isContainerPresent(request.getContainerName());
        assertThat(isPresent).isTrue();
        // cleaning
        dockerClientInstance.removeContainer(request.getContainerName());
    }
    //endregion

    //region isContainerActive
    @Test
    void shouldIsContainerActiveBeTrue() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        request.setCmd("sh -c 'sleep 10 && echo Hello from Docker alpine!'");
        dockerClientInstance.createContainer(request);
        boolean isStarted = dockerClientInstance.startContainer(request.getContainerName());
        assertThat(isStarted).isTrue();

        boolean isActive = dockerClientInstance
                .isContainerActive(request.getContainerName());
        assertThat(isActive).isTrue();
        // cleaning
        dockerClientInstance.stopAndRemoveContainer(request.getContainerName());
    }

    @Test
    void shouldIsContainerActiveBeFalseSinceContainerIsNotRunning() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        dockerClientInstance.createContainer(request);
        // Container is not running or restarting

        boolean isActive = dockerClientInstance
                .isContainerActive(request.getContainerName());
        assertThat(isActive).isFalse();
        // cleaning
        dockerClientInstance.removeContainer(request.getContainerName());
    }
    //endregion

    //region getContainerName
    @Test
    void shouldGetContainerName() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        String containerId = dockerClientInstance.createContainer(request);

        assertThat(dockerClientInstance.getContainerName(containerId))
                .isEqualTo(request.getContainerName());

        // cleaning
        dockerClientInstance.removeContainer(request.getContainerName());
    }

    @Test
    void shouldNotGetContainerNameSinceEmptyId() {
        assertThat(dockerClientInstance.getContainerName("")).isEmpty();
    }

    @Test
    void shouldNotGetContainerNameSinceNoContainer() {
        assertThat(dockerClientInstance.getContainerName(getRandomString())).isEmpty();
    }

    @Test
    void shouldNotGetContainerNameSinceDockerCmdException() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        String containerId = dockerClientInstance.createContainer(request);
        assertThat(corruptClientInstance.getContainerName(containerId)).isEmpty();
        dockerClientInstance.removeContainer(request.getContainerName());
    }
    //endregion

    //region getContainerId
    @Test
    void shouldGetContainerId() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        pullImageIfNecessary();
        String expectedId = dockerClientInstance.createContainer(request);

        String containerId =
                dockerClientInstance.getContainerId(request.getContainerName());
        assertThat(containerId)
                .isNotEmpty()
                .isEqualTo(expectedId);

        // cleaning
        dockerClientInstance.removeContainer(request.getContainerName());
    }

    @Test
    void shouldNotGetContainerIdSinceEmptyId() {
        assertThat(dockerClientInstance.getContainerId("")).isEmpty();
    }

    @Test
    void shouldNotGetContainerIdSinceDockerCmdException() {
        assertThat(corruptClientInstance.getContainerId(getRandomString())).isEmpty();
    }
    //endregion

    //region getContainerStatus
    @Test
    void shouldGetContainerStatus() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        pullImageIfNecessary();
        dockerClientInstance.createContainer(request);

        assertThat(dockerClientInstance.getContainerStatus(request.getContainerName()))
                .isEqualTo(DockerClientInstance.CREATED_STATUS);

        // cleaning
        dockerClientInstance.removeContainer(request.getContainerName());
    }

    @Test
    void shouldNotGetContainerStatusSinceEmptyId() {
        assertThat(dockerClientInstance.getContainerStatus("")).isEmpty();
    }

    @Test
    void shouldNotGetContainerStatusSinceDockerCmdException() {
        assertThat(corruptClientInstance.getContainerStatus(getRandomString())).isEmpty();
    }
    //endregion

    //region startContainer
    @Test
    void shouldStartContainer() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        String containerName = request.getContainerName();
        request.setCmd("sh -c 'sleep 1 && echo Hello from Docker alpine!'");
        pullImageIfNecessary();
        dockerClientInstance.createContainer(request);

        assertThat(dockerClientInstance.startContainer(containerName)).isTrue();
        assertThat(dockerClientInstance.getContainerStatus(containerName))
                .isEqualTo(DockerClientInstance.RUNNING_STATUS);

        // cleaning
        dockerClientInstance.stopContainer(containerName);
        dockerClientInstance.removeContainer(containerName);
    }

    @Test
    void shouldNotStartContainerNameSinceEmptyId() {
        assertThat(dockerClientInstance.startContainer("")).isFalse();
    }

    @Test
    void shouldNotStartContainerSinceDockerCmdException() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        final String containerName = request.getContainerName();
        dockerClientInstance.createContainer(request);
        assertThat(corruptClientInstance.startContainer(containerName)).isFalse();
        dockerClientInstance.removeContainer(containerName);
    }
    //endregion

    //region waitContainerUntilExitOrTimeout
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {""})
    void shouldNotWaitContainerUntilExitOrTimeoutSinceBlankContainerName(String containerName) {
        final String message = assertThrows(IllegalArgumentException.class, () -> dockerClientInstance.waitContainerUntilExitOrTimeout(containerName, null))
                .getMessage();
        assertEquals("Container name cannot be blank", message);
    }

    @Test
    void shouldNotWaitContainerUntilExitOrTimeoutSinceNoTimeout() {
        final String message = assertThrows(IllegalArgumentException.class, () -> dockerClientInstance.waitContainerUntilExitOrTimeout("dummyContainerName", null))
                .getMessage();
        assertEquals("Timeout date cannot be null", message);
    }

    @Test
    void shouldTimeoutAfterWaitContainerUntilExitOrTimeout() throws TimeoutException {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        String containerName = request.getContainerName();
        request.setCmd("sh -c 'sleep 30 && echo Hello from Docker alpine!'");
        pullImageIfNecessary();
        dockerClientInstance.createContainer(request);
        dockerClientInstance.startContainer(containerName);
        assertThat(dockerClientInstance.getContainerStatus(containerName))
                .isEqualTo(DockerClientInstance.RUNNING_STATUS);
        Date before = new Date();

        assertThrows(TimeoutException.class, () -> dockerClientInstance.waitContainerUntilExitOrTimeout(containerName,
                Instant.now().plusSeconds(5)));
        assertThat(dockerClientInstance.getContainerStatus(containerName))
                .isEqualTo(DockerClientInstance.RUNNING_STATUS);
        assertThat(new Date().getTime() - before.getTime()).isGreaterThan(1000);
        // cleaning
        dockerClientInstance.stopContainer(containerName);
        dockerClientInstance.removeContainer(containerName);
    }

    @Test
    void shouldWaitContainerUntilExitOrTimeoutSinceExited() throws TimeoutException {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        String containerName = request.getContainerName();
        request.setCmd("sh -c 'sleep 1 && echo Hello from Docker alpine!'");
        dockerClientInstance.createContainer(request);
        dockerClientInstance.startContainer(containerName);
        assertThat(dockerClientInstance.getContainerStatus(containerName))
                .isEqualTo(DockerClientInstance.RUNNING_STATUS);
        dockerClientInstance.waitContainerUntilExitOrTimeout(containerName,
                Instant.now().plusMillis(3000));
        assertThat(dockerClientInstance.getContainerStatus(containerName))
                .isEqualTo(DockerClientInstance.EXITED_STATUS);

        // cleaning
        dockerClientInstance.stopContainer(containerName);
        dockerClientInstance.removeContainer(containerName);
    }
    //endregion

    //region getContainerExitCode
    @Test
    void shouldGetContainerExitCode() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        dockerClientInstance.createContainer(request);
        int exitCode = dockerClientInstance
                .getContainerExitCode(request.getContainerName());
        assertThat(exitCode).isZero();
        dockerClientInstance.removeContainer(request.getContainerName());
    }

    @Test
    void shouldNotGetContainerExitCodeSinceDockerCmdException() {
        assertThat(corruptClientInstance.getContainerExitCode(getRandomString()))
                .isEqualTo(-1);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {""})
    void shouldNotGetContainerExitCodeSinceBlankContainerName(String containerName) {
        final String message = assertThrows(IllegalArgumentException.class, () -> dockerClientInstance.getContainerExitCode(containerName))
                .getMessage();
        assertEquals("Container name cannot be blank", message);
    }
    //endregion

    //region getContainerLogs
    @Test
    void shouldGetContainerLogsSinceStdout() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        request.setCmd("sh -c 'echo Hello from Docker alpine!'");
        pullImageIfNecessary();
        dockerClientInstance.createContainer(request);
        dockerClientInstance.startContainer(request.getContainerName());

        Optional<DockerLogs> containerLogs =
                dockerClientInstance.getContainerLogs(request.getContainerName());
        assertThat(containerLogs).isPresent();
        assertThat(containerLogs.get().getStdout()).contains("Hello from " +
                "Docker alpine!");
        assertThat(containerLogs.get().getStderr()).isEmpty();

        // cleaning
        dockerClientInstance.stopContainer(request.getContainerName());
        dockerClientInstance.removeContainer(request.getContainerName());
    }

    @Test
    void shouldGetContainerLogsSinceStderr() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        request.setCmd("sh -c 'echo Hello from Docker alpine! >&2'");
        pullImageIfNecessary();
        dockerClientInstance.createContainer(request);
        dockerClientInstance.startContainer(request.getContainerName());

        Optional<DockerLogs> containerLogs =
                dockerClientInstance.getContainerLogs(request.getContainerName());
        assertThat(containerLogs).isPresent();
        assertThat(containerLogs.get().getStdout()).isEmpty();
        assertThat(containerLogs.get().getStderr()).contains("Hello from " +
                "Docker alpine!");

        // cleaning
        dockerClientInstance.stopContainer(request.getContainerName());
        dockerClientInstance.removeContainer(request.getContainerName());
    }

    @Test
    void shouldNotGetContainerLogsSinceEmptyId() {
        assertThat(dockerClientInstance.getContainerLogs("")).isEmpty();
    }

    @Test
    void shouldNotGetContainerLogsSinceNoContainer() {
        final String containerName = getRandomString();
        assertThat(dockerClientInstance.getContainerLogs(containerName)).isEmpty();
    }

    @Test
    void shouldNotGetContainerLogsSinceDockerCmdException() {
        final String containerName = getRandomString();
        when(corruptClientInstance.isContainerPresent(containerName)).thenReturn(true);
        assertThat(corruptClientInstance.getContainerLogs(containerName)).isEmpty();
    }

    @Test
    void shouldNotGetContainerLogsSinceInterruptedException(CapturedOutput output) throws InterruptedException {
        final String containerName = getRandomString();
        final DockerClient dockerClient = mock(DockerClient.class);
        final LogContainerCmd logContainerCmd = mock(LogContainerCmd.class);
        final DockerClientInstance.FrameResultCallback resultCallback = mock(DockerClientInstance.FrameResultCallback.class);
        ReflectionTestUtils.setField(dockerClientInstance, "client", dockerClient);
        doReturn(true).when(dockerClientInstance).isContainerPresent(containerName);
        when(dockerClient.logContainerCmd(containerName)).thenReturn(logContainerCmd);
        when(logContainerCmd.withStdOut(true)).thenReturn(logContainerCmd);
        when(logContainerCmd.withStdErr(true)).thenReturn(logContainerCmd);
        when(logContainerCmd.exec(any())).thenReturn(resultCallback);
        when(resultCallback.awaitCompletion()).thenThrow(InterruptedException.class);
        dockerClientInstance.getContainerLogs(containerName);
        assertThat(output.getOut()).contains("Docker logs command was interrupted");
        verify(logContainerCmd).exec(any());
        verify(dockerClient).logContainerCmd(containerName);
    }
    //endregion

    //region stopContainer
    @Test
    void shouldStopContainer() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        String containerName = request.getContainerName();
        request.setCmd("sh -c 'sleep 10 && echo Hello from Docker alpine!'");
        pullImageIfNecessary();
        dockerClientInstance.createContainer(request);
        dockerClientInstance.startContainer(containerName);
        assertThat(dockerClientInstance.getContainerStatus(containerName))
                .isEqualTo(DockerClientInstance.RUNNING_STATUS);

        boolean isStopped = dockerClientInstance.stopContainer(containerName);
        assertThat(isStopped).isTrue();
        assertThat(dockerClientInstance.getContainerStatus(containerName))
                .isEqualTo(DockerClientInstance.EXITED_STATUS);
        verify(dockerClientInstance, atLeastOnce()).isContainerPresent(containerName);
        verify(dockerClientInstance).isContainerActive(containerName);
        // cleaning
        dockerClientInstance.removeContainer(containerName);
    }

    @Test
    void shouldReturnTrueWhenContainerIsNotActive() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        String containerName = request.getContainerName();
        request.setCmd("sh -c 'sleep 10 && echo Hello from Docker alpine!'");
        pullImageIfNecessary();
        dockerClientInstance.createContainer(request);
        assertThat(dockerClientInstance.getContainerStatus(containerName))
                .isEqualTo(DockerClientInstance.CREATED_STATUS);
        // Use spied client to verify method calls
        when(dockerClientInstance.getClient()).thenReturn(spiedClient);

        boolean isStopped = dockerClientInstance.stopContainer(containerName);
        assertThat(isStopped).isTrue();
        verify(dockerClientInstance, atLeastOnce()).isContainerPresent(containerName);
        verify(dockerClientInstance).isContainerActive(containerName);
        verify(spiedClient, never()).stopContainerCmd(anyString());
        // cleaning
        dockerClientInstance.removeContainer(containerName);
    }

    @Test
    void shouldNotStopContainerSinceEmptyId() {
        assertThat(dockerClientInstance.stopContainer("")).isFalse();
    }

    @Test
    void shouldNotStopContainerSinceNotFound() {
        String containerName = "not-found";
        boolean isStopped = dockerClientInstance.stopContainer(containerName);
        assertThat(isStopped).isFalse();
        verify(dockerClientInstance, atLeastOnce()).isContainerPresent(containerName);
        verify(dockerClientInstance, never()).isContainerActive(containerName);
    }

    @Test
    void shouldNotStopContainerSinceDockerCmdException() {
        final String containerName = getRandomString();
        when(corruptClientInstance.isContainerPresent(containerName)).thenReturn(true);
        when(corruptClientInstance.isContainerActive(containerName)).thenReturn(true);
        assertThat(corruptClientInstance.stopContainer(containerName)).isFalse();
    }
    //endregion

    //region removeContainer
    @Test
    void shouldRemoveContainer() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        String containerName = request.getContainerName();
        request.setCmd("sh -c 'sleep 10 && echo Hello from Docker alpine!'");
        dockerClientInstance.createContainer(request);
        dockerClientInstance.startContainer(containerName);
        dockerClientInstance.stopContainer(containerName);

        assertThat(dockerClientInstance.removeContainer(containerName)).isTrue();
    }

    @Test
    void shouldNotRemoveContainerSinceEmptyId() {
        assertThat(dockerClientInstance.removeContainer("")).isFalse();
    }

    @Test
    void shouldNotRemoveContainerSinceRunning() {
        DockerRunRequest request = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        String containerName = request.getContainerName();
        request.setCmd("sh -c 'sleep 5 && echo Hello from Docker alpine!'");
        pullImageIfNecessary();
        dockerClientInstance.createContainer(request);
        dockerClientInstance.startContainer(containerName);

        assertThat(dockerClientInstance.getContainerStatus(containerName))
                .isEqualTo(DockerClientInstance.RUNNING_STATUS);
        assertThat(dockerClientInstance.removeContainer(containerName)).isFalse();
        // cleaning
        dockerClientInstance.stopAndRemoveContainer(containerName);
    }

    @Test
    void shouldNotRemoveContainerSinceDockerCmdException() {
        final String containerName = getRandomString();
        when(corruptClientInstance.isContainerPresent(containerName)).thenReturn(true);
        assertThat(corruptClientInstance.removeContainer(containerName)).isFalse();
    }
    //endregion

    // region getContainerExecutionDuration
    @Test
    void shouldGetDurationOnFinishedContainer() throws TimeoutException {
        final DockerRunRequest dockerRunRequest = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        dockerRunRequest.setMaxExecutionTime(5000); // 5s
        final String msg = "Hello world!";
        dockerRunRequest.setCmd("sh -c 'echo " + msg + "'");
        final String containerName = dockerRunRequest.getContainerName();

        try {
            dockerClientInstance.createContainer(dockerRunRequest);
            dockerClientInstance.startContainer(containerName);
            final Instant timeoutDate = Instant.now()
                    .plusMillis(dockerRunRequest.getMaxExecutionTime());
            dockerClientInstance.waitContainerUntilExitOrTimeout(containerName, timeoutDate);

            final Optional<Duration> executionDuration = dockerClientInstance.getContainerExecutionDuration(containerName);
            assertThat(executionDuration).isPresent();
            assertThat(executionDuration.get()).isGreaterThanOrEqualTo(Duration.ZERO);
        } finally {
            dockerClientInstance.removeContainer(containerName);
        }
    }

    @Test
    void shouldNotGetDurationOnNotStartedContainer() {
        final DockerRunRequest dockerRunRequest = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        dockerRunRequest.setMaxExecutionTime(5000); // 5s
        final String msg = "Hello world!";
        dockerRunRequest.setCmd("sh -c 'sleep 2 && echo " + msg + "'");
        final String containerName = dockerRunRequest.getContainerName();
        try {
            dockerClientInstance.createContainer(dockerRunRequest);

            final Optional<Duration> executionDuration = dockerClientInstance.getContainerExecutionDuration(containerName);
            assertThat(executionDuration).isEmpty();
        } finally {
            dockerClientInstance.removeContainer(containerName);
        }
    }

    @Test
    void shouldNotGetDurationOnNotFinishedContainer() {
        final DockerRunRequest dockerRunRequest = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        dockerRunRequest.setMaxExecutionTime(60000); // 60s
        final String msg = "Hello world!";
        dockerRunRequest.setCmd("sh -c 'sleep 60 && echo " + msg + "'");
        final String containerName = dockerRunRequest.getContainerName();
        try {
            dockerClientInstance.createContainer(dockerRunRequest);
            dockerClientInstance.startContainer(containerName);

            final Optional<Duration> executionDuration = dockerClientInstance.getContainerExecutionDuration(containerName);
            assertThat(executionDuration).isEmpty();
        } finally {
            dockerClientInstance.removeContainer(containerName);
        }
    }

    @Test
    void shouldNotGetExecutionDurationForNonExistingContainer() {
        final Optional<Duration> executionDuration = dockerClientInstance
                .getContainerExecutionDuration("NonExistingContainer");
        assertThat(executionDuration).isEmpty();
    }

    @Test
    void shouldReturnZeroWhenStartedAfterFinished() {
        final DockerRunRequest dockerRunRequest = getDefaultDockerRunRequest(SgxDriverMode.NONE);
        // Docker has seen the `exit` event 0.4 ms before the `started` event
        final String startedAt = "2023-10-26T12:24:40.163261033Z";
        final String finishedAt = "2023-10-26T12:24:40.16305078Z";
        final Optional<Duration> executionDuration = dockerClientInstance.getContainerExecutionDuration(
                dockerRunRequest.getContainerName(),
                startedAt,
                finishedAt
        );
        assertThat(executionDuration).contains(Duration.ZERO);
    }
    // endregion

    // tools

    @Override
    String getRandomString() {
        String random = RandomStringUtils.randomAlphanumeric(20);
        usedRandomNames.add(random);
        return random;
    }

    private void pullImageIfNecessary() {
        if (dockerClientInstance.getImageId(ALPINE_LATEST).isEmpty()) {
            dockerClientInstance.pullImage(ALPINE_LATEST);
        }
    }
}
