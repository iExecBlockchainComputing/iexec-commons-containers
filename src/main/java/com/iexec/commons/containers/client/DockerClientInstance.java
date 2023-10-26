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
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.NameParser;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.iexec.commons.containers.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class DockerClientInstance {

    // default docker registry address
    public static final String DEFAULT_DOCKER_REGISTRY = "docker.io";

    public static final String CREATED_STATUS = "created";
    public static final String RUNNING_STATUS = "running";
    public static final String RESTARTING_STATUS = "restarting";
    public static final String EXITED_STATUS = "exited";

    private final DockerClient client;

    /**
     * Create a new unauthenticated Docker client instance with the default Docker registry
     * {@link DockerClientInstance#DEFAULT_DOCKER_REGISTRY}.
     */
    DockerClientInstance() {
        this.client = createClient(DEFAULT_DOCKER_REGISTRY, "", "");
    }

    /**
     * Create a new unauthenticated Docker client instance with the specified Docker registry
     * address.
     * 
     * @param registryAddress
     * @throws IllegalArgumentException if registry address is blank
     */
    DockerClientInstance(String registryAddress) {
        if (StringUtils.isBlank(registryAddress)) {
            throw new IllegalArgumentException("Docker registry address must not be blank");
        }
        this.client = createClient(registryAddress, "", "");
    }

    /**
     * Create a new authenticated Docker client instance. The created client will be
     * authenticated against the provided registry.
     * 
     * @param registryAddress e.g. {@code https://index.docker.io/v1/, https://nexus.iex.ec,
     *                          docker.io, nexus.iex.ec}
     * @param username
     * @param password
     */
    DockerClientInstance(String registryAddress, String username, String password) {
        if (StringUtils.isBlank(registryAddress)) {
            throw new IllegalArgumentException("Docker registry address must not be blank");
        }
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("Docker registry username must not be blank");
        }
        if (StringUtils.isBlank(password)) {
            throw new IllegalArgumentException("Docker registry password must not be blank");
        }
        this.client = createClient(registryAddress, username, password);
    }

    public DockerClient getClient() {
        return this.client;
    }

    //region volume
    public synchronized boolean createVolume(String volumeName) {
        if (StringUtils.isBlank(volumeName)) {
            logInvalidVolumeNameError(volumeName);
            return false;
        }
        if (isVolumePresent(volumeName)) {
            log.info("Docker volume already present [name:{}]", volumeName);
            return true;
        }
        try (CreateVolumeCmd createVolumeCmd = getClient().createVolumeCmd()) {
            String name = createVolumeCmd
                    .withName(volumeName)
                    .exec()
                    .getName();
            if (name == null || !name.equals(volumeName)) {
                return false;
            }
            log.info("Created docker volume [name:{}]", volumeName);
            return true;
        } catch (Exception e) {
            log.error("Error creating docker volume [name:{}]", volumeName, e);
            return false;
        }
    }

    public boolean isVolumePresent(String volumeName) {
        return getVolume(volumeName).isPresent();
    }

    public Optional<InspectVolumeResponse> getVolume(String volumeName) {
        if (StringUtils.isBlank(volumeName)) {
            logInvalidVolumeNameError(volumeName);
            return Optional.empty();
        }
        try (ListVolumesCmd listVolumesCmd = getClient().listVolumesCmd()) {
            List<InspectVolumeResponse> volumes = listVolumesCmd
                    .withFilter("name", Collections.singletonList(volumeName))
                    .exec()
                    .getVolumes();
            List<InspectVolumeResponse> filtered = volumes.stream()
                    .filter(volume -> volumeName.equals(volume.getName()))
                    .collect(Collectors.toList());
            return filtered.stream().findFirst();
        } catch (Exception e) {
            log.error("Error getting docker volume [name:{}]", volumeName, e);
            return Optional.empty();
        }
    }

    public synchronized boolean removeVolume(String volumeName) {
        if (StringUtils.isBlank(volumeName)) {
            logInvalidVolumeNameError(volumeName);
            return false;
        }
        try (RemoveVolumeCmd removeVolumeCmd = getClient().removeVolumeCmd(volumeName)) {
            removeVolumeCmd.exec();
            log.info("Removed docker volume [name:{}]", volumeName);
            return true;
        } catch (NotFoundException e) {
            log.warn("No docker volume to remove [name:{}]", volumeName);
        } catch (Exception e) {
            log.error("Error removing docker volume [name:{}]", volumeName, e);
        }
        return false;
    }

    private void logInvalidVolumeNameError(String volumeName) {
        log.error("Invalid docker volume name [name:{}]", volumeName);
    }
    //endregion

    //region network
    public synchronized String createNetwork(String networkName) {
        if (StringUtils.isBlank(networkName)) {
            logInvalidNetworkNameError(networkName);
            return "";
        }
        if (isNetworkPresent(networkName)) {
            log.info("Docker network already present [name:{}]", networkName);
            return getNetworkId(networkName);
        }
        try (CreateNetworkCmd networkCmd = getClient().createNetworkCmd()) {
            String id = networkCmd
                    .withName(networkName)
                    .withDriver("bridge")
                    .exec()
                    .getId();
            if (id == null) {
                return "";
            }
            log.info("Created docker network [name:{}]", networkName);
            return id;
        } catch (Exception e) {
            log.error("Error creating docker network [name:{}]", networkName, e);
            return "";
        }
    }

    public String getNetworkId(String networkName) {
        if (StringUtils.isBlank(networkName)) {
            logInvalidNetworkNameError(networkName);
            return "";
        }
        try (ListNetworksCmd listNetworksCmd = getClient().listNetworksCmd()) {
            return listNetworksCmd
                    .withNameFilter(networkName)
                    .exec()
                    .stream()
                    .filter(network -> StringUtils.isNotBlank(network.getName()))
                    .filter(network -> network.getName().equals(networkName))
                    .map(Network::getId)
                    .findFirst()
                    .orElse("");
        } catch (Exception e) {
            log.error("Error getting network id [name:{}]", networkName, e);
            return "";
        }
    }

    public boolean isNetworkPresent(String networkName) {
        return !getNetworkId(networkName).isEmpty();
    }

    public synchronized boolean removeNetwork(String networkName) {
        if (StringUtils.isBlank(networkName)) {
            logInvalidNetworkNameError(networkName);
            return false;
        }
        try (RemoveNetworkCmd removeNetworkCmd =
                     getClient().removeNetworkCmd(networkName)) {
            removeNetworkCmd.exec();
            log.info("Removed docker network [name:{}]", networkName);
            return true;
        } catch (NotFoundException e) {
            log.warn("No docker network to remove [name:{}]", networkName);
        } catch (Exception e) {
            log.error("Error removing docker network [name:{}]", networkName, e);
        }
        return false;
    }

    private void logInvalidNetworkNameError(String networkName) {
        log.error("Invalid docker network name [name:{}]", networkName);
    }
    //endregion

    //region image
    /**
     * Pull docker image and timeout after 1 minute.
     * 
     * @param imageName Name of the image to pull
     * @return true if image is pulled successfully,
     * false otherwise.
     */
    public boolean pullImage(String imageName) {
        return pullImage(imageName, Duration.of(1, ChronoUnit.MINUTES));
    }

    /**
     * Pull docker image and timeout after given duration.
     *
     * @param imageName Name of the image to pull
     * @param timeout Duration to wait before timeout
     * @return true if image is pulled successfully,
     * false otherwise.
     */
    public boolean pullImage(String imageName, Duration timeout) {
        if (StringUtils.isBlank(imageName)) {
            log.error("Invalid docker image name [name:{}]", imageName);
            return false;
        }
        NameParser.ReposTag repoAndTag = NameParser.parseRepositoryTag(imageName);
        if (StringUtils.isBlank(repoAndTag.repos)
                || StringUtils.isBlank(repoAndTag.tag)) {
            log.error("Error parsing docker image name [name:{}, repo:{}, tag:{}]",
                    imageName, repoAndTag.repos, repoAndTag.tag);
            return false;
        }
        PullImageResultCallback callback = new PullImageResultCallback();
        try (PullImageCmd pullImageCmd =
                     getClient().pullImageCmd(repoAndTag.repos)) {
            log.info("Pulling docker image [name:{}]", imageName);
            boolean isPulledBeforeTimeout = pullImageCmd
                    .withTag(repoAndTag.tag)
                    .exec(callback)
                    .awaitCompletion(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!isPulledBeforeTimeout) {
                log.error("Docker image has not been pulled (timeout) [name:{}, timeout:{}s]",
                        imageName, timeout.toSeconds());
                return false;
            }
            log.info("Pulled docker image [name:{}]", imageName);
            return true;
        } catch (InterruptedException e) {
            log.error("Docker pull command was interrupted [name:{}]", imageName, e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error pulling docker image [name:{}]", imageName, e);
        }
        return false;
    }

    public String getImageId(String imageName) {
        if (StringUtils.isBlank(imageName)) {
            log.error("Invalid docker image name [name:{}]", imageName);
            return "";
        }
        String sanitizedImageName = sanitizeImageName(imageName);

        try (ListImagesCmd listImagesCmd = getClient().listImagesCmd()) {
            return listImagesCmd
                    .withDanglingFilter(false)
                    .withImageNameFilter(sanitizedImageName)
                    .exec()
                    .stream()
                    .filter(image -> image.getRepoTags() != null && image.getRepoTags().length > 0)
                    .filter(image -> Arrays.asList(image.getRepoTags()).contains(sanitizedImageName))
                    .map(Image::getId)
                    .findFirst()
                    .orElse("");
        } catch (Exception e) {
            log.error("Error getting docker image id [name:{}]", imageName, e);
            return "";
        }
    }

    /**
     * Remove "docker.io" and "library" from image name.
     * @param image
     * @return
     */
    public String sanitizeImageName(String image) {
        List<String> regexList = Arrays.asList( // order matters
                "docker.io/library/(.*)", // docker.io/library/alpine:latest
                "library/(.*)", // library/alpine:latest
                "docker.io/(.*)"); // docker.io/repo/image:latest

        for (String regex : regexList) {
            Matcher m = Pattern.compile(regex).matcher(image);
            if (m.find()) {
                return m.group(1);
            }
        }
        return image;
    }

    public boolean isImagePresent(String imageName) {
        return !getImageId(imageName).isEmpty();
    }

    public synchronized boolean removeImage(String imageName) {
        if (StringUtils.isBlank(imageName)) {
            log.error("Docker image name cannot be blank");
            return false;
        }
        if (!isImagePresent(imageName)) {
            log.info("No docker image to remove [name:{}]", imageName);
            return false;
        }
        try (RemoveImageCmd removeImageCmd =
                    getClient().removeImageCmd(imageName)) {
            removeImageCmd.exec();
            log.info("Removed docker image [name:{}]", imageName);
            return true;
        } catch (Exception e) {
            log.error("Error removing docker image [name:{}]", imageName, e);
            return false;
        }
    }
    //endregion

    //region container
    /**
     * Run a docker container with the specified config.
     * If maxExecutionTime is less or equal to 0, the container
     * will run in detached mode, thus, we return immediately
     * without waiting for it to exit.
     * 
     * @param dockerRunRequest config of the run
     * @return a response with metadata and success or failure
     * status.
     */
    public DockerRunResponse run(DockerRunRequest dockerRunRequest) {
        log.info("Running docker container [name:{}, image:{}, cmd:{}]",
                dockerRunRequest.getContainerName(), dockerRunRequest.getImageUri(),
                dockerRunRequest.getArrayArgsCmd());
        DockerRunResponse dockerRunResponse = DockerRunResponse.builder()
                .finalStatus(DockerRunFinalStatus.FAILED)
                .containerExitCode(-1)
                .build();
        String containerName = dockerRunRequest.getContainerName();
        // TODO choose to remove duplicate containers or not
        if (createContainer(dockerRunRequest).isEmpty()) {
            log.error("Failed to create container for docker run [name:{}]", containerName);
            return dockerRunResponse;
        }
        if (!startContainer(containerName)) {
            log.error("Failed to start container for docker run [name:{}]", containerName);
            removeContainer(containerName);
            return dockerRunResponse;
        }
        if (dockerRunRequest.getMaxExecutionTime() <= 0) {
            // container will run until self-exited or explicitly-stopped
            log.info("Docker container will run in detached mode [name:{}]", containerName);
            dockerRunResponse.setFinalStatus(DockerRunFinalStatus.SUCCESS);
            return dockerRunResponse;
        }
        Instant timeoutDate = Instant.now()
                .plusMillis(dockerRunRequest.getMaxExecutionTime());
        boolean isSuccessful;
        try {
            int exitCode = waitContainerUntilExitOrTimeout(containerName, timeoutDate);
            dockerRunResponse.setContainerExitCode(exitCode);

            isSuccessful = exitCode == 0L;
            log.info("Finished running docker container [name:{}, isSuccessful:{}]",
                    containerName, isSuccessful);
            dockerRunResponse.setFinalStatus(
                    isSuccessful
                            ? DockerRunFinalStatus.SUCCESS
                            : DockerRunFinalStatus.FAILED);
        } catch (TimeoutException e) {
            log.error(e.getMessage());
            dockerRunResponse.setFinalStatus(DockerRunFinalStatus.TIMEOUT);
            if (!stopContainer(containerName)) {
                getContainerLogs(containerName).ifPresent(dockerRunResponse::setDockerLogs);
                log.error("Failed to force-stop container after timeout [name:{}]", containerName);
                return dockerRunResponse;
            }
        } catch (IllegalArgumentException e) {
            log.error(e.getMessage());
        }

        getContainerLogs(containerName).ifPresent(dockerRunResponse::setDockerLogs);
        getContainerExecutionDuration(containerName).ifPresent(dockerRunResponse::setExecutionDuration);
        if (!removeContainer(containerName)) {
            log.warn("Failed to remove container after run [name:{}]", containerName);
        }
        return dockerRunResponse;
    }

    public boolean stopAndRemoveContainer(String containerName) {
        stopContainer(containerName);
        removeContainer(containerName);
        return isContainerPresent(containerName);
    }

    /**
     * Create docker container and remove existing duplicate if found.
     * 
     * @param dockerRunRequest Container creation parameters
     * @return A container ID if a container was successfully created or an empty string otherwise
     */
    public String createContainer(DockerRunRequest dockerRunRequest) {
        return createContainer(dockerRunRequest, true);
    }

    /**
     * Create docker container and choose whether to
     * remove existing duplicate container (if found)
     * or not.
     * 
     * @param dockerRunRequest Container creation parameters
     * @param removeDuplicate Whether to remove or not an existing container with the same name
     * @return A container ID if a container was successfully created or an empty string otherwise
     */
    public synchronized String createContainer(DockerRunRequest dockerRunRequest, boolean removeDuplicate) {
        if (dockerRunRequest == null
                || StringUtils.isBlank(dockerRunRequest.getImageUri())
                || StringUtils.isBlank(dockerRunRequest.getContainerName())) {
            log.error("Invalid docker run request [dockerRunRequest:{}]", dockerRunRequest);
            return "";
        }
        String containerName = dockerRunRequest.getContainerName();
        // clean duplicate if present
        if (isContainerPresent(containerName)) {
            log.info("Found duplicate container [name:{}, oldContainerId:{}, removeDuplicate:{}]",
                    containerName, getContainerId(containerName), removeDuplicate);
            if (!removeDuplicate) {
                return "";
            }
            stopContainer(containerName);
            removeContainer(containerName);
        }
        // create network if needed
        String networkName = dockerRunRequest.getHostConfig().getNetworkMode();
        if (StringUtils.isNotBlank(networkName)
                && StringUtils.isBlank(createNetwork(networkName))) {
            log.error("Failed to create network for the container [name:{}, networkName:{}]",
                    containerName, networkName);
            return "";
        }
        // create container
        try (CreateContainerCmd createContainerCmd = getClient()
                .createContainerCmd(dockerRunRequest.getImageUri())) {
            String containerId =
                    buildCreateContainerCmdFromRunRequest(dockerRunRequest, createContainerCmd)
                            .map(CreateContainerCmd::exec)
                            .map(CreateContainerResponse::getId)
                            .orElse("");
            if (StringUtils.isNotBlank(containerId)) {
                log.info("Created docker container [name:{}, id:{}]",
                        containerName, containerId);
            }
            return containerId;
        } catch (Exception e) {
            log.error("Error creating docker container [name:{}]", containerName, e);
            return "";
        }
    }

    /**
     * Params of the DockerRunRequest need to be passed to the CreateContainerCmd
     * when creating a container
     *
     * @param dockerRunRequest contains information for creating container
     * @return a populated CreateContainerCmd
     */
    public Optional<CreateContainerCmd> buildCreateContainerCmdFromRunRequest(
            DockerRunRequest dockerRunRequest,
            CreateContainerCmd createContainerCmd
    ) {
        if (dockerRunRequest == null || createContainerCmd == null) {
            return Optional.empty();
        }
        createContainerCmd
                .withHostConfig(dockerRunRequest.getHostConfig())
                .withName(dockerRunRequest.getContainerName());
        if (StringUtils.isNotBlank(dockerRunRequest.getCmd())) {
            createContainerCmd.withCmd(
                    ArgsUtils.stringArgsToArrayArgs(dockerRunRequest.getCmd()));
        }
        // here the entrypoint can be an empty string
        // to override the default behavior
        if (dockerRunRequest.getEntrypoint() != null) {
            createContainerCmd.withEntrypoint(
                    ArgsUtils.stringArgsToArrayArgs(dockerRunRequest.getEntrypoint()));
        }
        if (dockerRunRequest.getEnv() != null && !dockerRunRequest.getEnv().isEmpty()) {
            createContainerCmd.withEnv(dockerRunRequest.getEnv());
        }
        if (dockerRunRequest.getContainerPort() > 0) {
            createContainerCmd.withExposedPorts(
                    new ExposedPort(dockerRunRequest.getContainerPort()));
        }
        if (dockerRunRequest.getWorkingDir() != null) {
            createContainerCmd.withWorkingDir(dockerRunRequest.getWorkingDir());
        }
        return Optional.of(createContainerCmd);
    }

    /**
     * @deprecated Use HostConfig field in DockerRunRequest
     */
    @Deprecated(forRemoval = true)
    public HostConfig buildHostConfigFromRunRequest(DockerRunRequest dockerRunRequest) {
        if (dockerRunRequest == null) {
            return null;
        }
        HostConfig hostConfig = HostConfig.newHostConfig();
        final String dockerNetworkName = dockerRunRequest.getDockerNetwork();
        if (StringUtils.isNotBlank(dockerNetworkName)) {
            hostConfig.withNetworkMode(dockerNetworkName);
        }
        final List<String> binds = dockerRunRequest.getBinds();
        if (!binds.isEmpty()) {
            hostConfig.withBinds(Binds.fromPrimitive(binds.toArray(new String[0])));
        }
        final List<Device> devices = dockerRunRequest.getDevices();
        hostConfig.withDevices(devices);
        return hostConfig;
    }

    public boolean isContainerPresent(String containerName) {
        return !getContainerId(containerName).isEmpty();
    }

    /**
     * Check if a container is active. The container is considered active
     * it is in one of the statuses {@code running} or {@code restarting}.
     * 
     * @param containerName name of the container
     * @return true if the container is in one of the active statuses,
     *         false otherwise.
     */
    public boolean isContainerActive(String containerName) {
        String currentContainerStatus = getContainerStatus(containerName);
        return List.of(RUNNING_STATUS, RESTARTING_STATUS)
                .contains(currentContainerStatus);
    }

    public String getContainerName(String containerId) {
        if (StringUtils.isBlank(containerId)) {
            log.error("Invalid docker container id [id:{}]", containerId);
            return "";
        }
        try (InspectContainerCmd inspectContainerCmd =
                     getClient().inspectContainerCmd(containerId)) {
            String name = inspectContainerCmd.exec().getName();
            // docker-java returns '/<container_id>' instead of '<container_id>'
            return name != null ? name.replace("/", "") : "";
        } catch (Exception e) {
            log.error("Error getting docker container name [id:{}]", containerId, e);
            return "";
        }
    }

    public String getContainerId(String containerName) {
        if (StringUtils.isBlank(containerName)) {
            log.error("Invalid docker container name [name:{}]", containerName);
            return "";
        }
        try (ListContainersCmd listContainersCmd = getClient().listContainersCmd()) {
            return listContainersCmd
                    .withShowAll(true)
                    .withNameFilter(Collections.singleton(containerName))
                    .exec()
                    .stream()
                    .findFirst()
                    .map(Container::getId)
                    .orElse("");
        } catch (Exception e) {
            log.error("Error getting docker container id [name:{}]", containerName, e);
            return "";
        }
    }

    public String getContainerStatus(String containerName) {
        if (StringUtils.isBlank(containerName)) {
            return "";
        }
        try (InspectContainerCmd inspectContainerCmd =
                    getClient().inspectContainerCmd(containerName)) {
            return inspectContainerCmd.exec()
                    .getState()
                    .getStatus();
        } catch (Exception e) {
            log.error("Error getting docker container status [name:{}]", containerName, e);
        }
        return "";
    }

    public synchronized boolean startContainer(String containerName) {
        if (StringUtils.isBlank(containerName)) {
            return false;
        }
        try (StartContainerCmd startContainerCmd =
                    getClient().startContainerCmd(containerName)) {
            startContainerCmd.exec();
            log.info("Started docker container [name:{}]", containerName);
            return true;
        } catch (Exception e) {
            log.error("Error starting docker container [name:{}]", containerName, e);
            return false;
        }
    }

    /**
     * Waits for full execution of a container (and stops waiting after a
     * particular date)
     * @param containerName name of the container to wait for
     * @param timeoutDate waiting is aborted once this date is reached
     * @return container's exit code (when relevant)
     */
    public int waitContainerUntilExitOrTimeout(
            String containerName,
            Instant timeoutDate
    ) throws TimeoutException {
        if (StringUtils.isBlank(containerName)) {
            throw new IllegalArgumentException("Container name cannot be blank");
        }
        if (timeoutDate == null) {
            throw new IllegalArgumentException("Timeout date cannot be null");
        }
        boolean isExited = false;
        boolean isTimeout = false;
        int seconds = 0;
        while (!isExited && !isTimeout) {
            if (seconds % 60 == 0) { // don't display logs too often
                log.info("Container is running [name:{}]", containerName);
            }
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                log.error("Sleep was interrupted [exception:{}]", e.getMessage());
                Thread.currentThread().interrupt();
            }
            isExited = getContainerStatus(containerName).equals(EXITED_STATUS);
            isTimeout = Instant.now().isAfter(timeoutDate);
            seconds++;
        }
        if (!isExited) {
            throw new TimeoutException(String.format("Container reached timeout [name:%s]", containerName));
        }
        int containerExitCode = getContainerExitCode(containerName);
        log.info("Container exited by itself [name:{}, exitCode:{}]",
                containerName, containerExitCode);
        return containerExitCode;
    }

    public int getContainerExitCode(String containerName) {
        if (StringUtils.isBlank(containerName)) {
            throw new IllegalArgumentException("Container name cannot be blank");
        }
        try (InspectContainerCmd inspectContainerCmd =
                     getClient().inspectContainerCmd(containerName)) {
            return inspectContainerCmd.exec()
                    .getState()
                    .getExitCodeLong().intValue();
        } catch (Exception e) {
            log.error("Error getting container exit code [name:{}]", containerName, e);
            return -1;
        }
    }

    public Optional<DockerLogs> getContainerLogs(String containerName) {
        if (StringUtils.isBlank(containerName)) {
            log.error("Invalid docker container name [name:{}]", containerName);
            return Optional.empty();
        }
        if (!isContainerPresent(containerName)) {
            log.error("Cannot get logs of inexistent docker container [name:{}]", containerName);
            return Optional.empty();
        }
        FrameResultCallback callback = new FrameResultCallback();
        try (LogContainerCmd logContainerCmd =
                     getClient().logContainerCmd(containerName)) {
            logContainerCmd
                    .withStdOut(true)
                    .withStdErr(true)
                    .exec(callback)
                    .awaitCompletion();
        } catch (InterruptedException e) {
            log.error("Docker logs command was interrupted [name:{}]", containerName, e);
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.error("Error getting docker container logs [name:{}]", containerName, e);
            return Optional.empty();
        }
        return Optional.of(DockerLogs.builder()
                .stdout(callback.getStdout())
                .stderr(callback.getStderr())
                .build());
    }

    /**
     * Stop a running docker container.
     * 
     * @param containerName name of the container to stop
     * @return true if the container was successfully stopped or its status
     * is not "running" or "restarting", false otherwise.
     */
    public synchronized boolean stopContainer(String containerName) {
        if (StringUtils.isEmpty(containerName)) {
            log.info("Invalid docker container name [name:{}]", containerName);
            return false;
        }
        if (!isContainerPresent(containerName)) {
            log.error("No docker container to stop [name:{}]", containerName);
            return false;
        }
        if (!isContainerActive(containerName)) {
            return true;
        }
        try (StopContainerCmd stopContainerCmd =
                    getClient().stopContainerCmd(containerName)) {
            stopContainerCmd
                    .withTimeout(0) // don't wait
                    .exec();
            log.info("Stopped docker container [name:{}]", containerName);
            return true;
        } catch (Exception e) {
            log.error("Error stopping docker container [name:{}]", containerName, e);
            return false;
        }
    }

    public synchronized boolean removeContainer(String containerName) {
        if (StringUtils.isBlank(containerName)) {
            log.error("Invalid docker container name [name:{}]", containerName);
            return false;
        }
        if (!isContainerPresent(containerName)) {
            log.info("No docker container to remove [name:{}]", containerName);
            return false;
        }
        try (RemoveContainerCmd removeContainerCmd =
                    getClient().removeContainerCmd(containerName)) {
            removeContainerCmd.exec();
            log.info("Removed docker container [name:{}]", containerName);
            return true;
        } catch (Exception e) {
            log.error("Error removing docker container [name:{}]", containerName, e);
            return false;
        }
    }

    /**
     * Retrieves the execution duration of a container.
     * If the container has not been started yet or has not ended yet,
     * then returns {@link Optional#empty()}.
     * Otherwise, returns the duration.
     * <p>
     * /!\ Docker inspection command precision could lead to sub-zero execution duration for fast containers.
     * In this case, this method returns a zero-duration object.
     *
     * @param containerName Name of the container to look for execution duration.
     * @return {@code Optional#empty()} if not started or ended,
     * the duration otherwise.
     */
    public Optional<Duration> getContainerExecutionDuration(String containerName) {
        try (InspectContainerCmd inspectContainerCmd = getClient().inspectContainerCmd(containerName)) {
            final InspectContainerResponse.ContainerState state = inspectContainerCmd.exec().getState();
            return getContainerExecutionDuration(containerName, state.getStartedAt(), state.getFinishedAt());
        } catch (DockerException e) {
            log.warn("Can't get execution duration of container [containerName:{}]", containerName, e);
            return Optional.empty();
        }
    }

    Optional<Duration> getContainerExecutionDuration(String containerName,
                                                            String startedAt,
                                                            String finishedAt) {
        final String defaultTime = "0001-01-01T00:00:00Z";
        if (defaultTime.equals(startedAt)) {
            log.debug("Container has not been started yet [containerName:{}]", containerName);
            return Optional.empty();
        }
        if (defaultTime.equals(finishedAt)) {
            log.debug("Container has not been ended yet [containerName:{}]", containerName);
            return Optional.empty();
        }

        final Instant startDate = Instant.parse(startedAt);
        final Instant endDate = Instant.parse(finishedAt);

        final Duration duration = Duration.between(startDate, endDate);
        if (duration.isNegative()) {
            // This could mean the command was wrong and has not been correctly executed
            log.debug("Container has finished faster than Docker precision [containerName:{}]", containerName);
            return Optional.of(Duration.ZERO);
        }
        return Optional.of(duration);
    }


    //endregion

    //region exec
    public Optional<DockerLogs> exec(String containerName, String... cmd) {
        if (StringUtils.isBlank(containerName)) {
            return Optional.empty();
        }
        if (!isContainerPresent(containerName)) {
            log.error("Cannot run docker exec since container not found [name:{}]",
                    containerName);
            return Optional.empty();
        }
        FrameResultCallback callback = new FrameResultCallback();
        // create 'docker exec' command
        try (ExecCreateCmd execCreateCmd = getClient().execCreateCmd(containerName)) {
            ExecCreateCmdResponse execCreateCmdResponse = execCreateCmd
                    .withAttachStderr(true)
                    .withAttachStdout(true)
                    .withCmd(cmd)
                    .exec();
            // run 'docker exec' command
            try (ExecStartCmd execStartCmd = getClient().execStartCmd(execCreateCmdResponse.getId())) {
                execStartCmd
                        .exec(callback)
                        .awaitCompletion();
            }
        } catch (InterruptedException e) {
            log.warn("Docker exec command was interrupted", e);
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.error("Error running docker exec command [name:{}, cmd:{}]",
                    containerName, cmd, e);
            return Optional.empty();
        }
        return Optional.of(DockerLogs.builder()
                .stdout(callback.getStdout())
                .stderr(callback.getStderr())
                .build());
    }
    //endregion

    /**
     * Build a new docker client instance. If credentials are provided, an authentication
     * attempt is made to the specified registry.
     * 
     * @param registryAddress
     * @param username
     * @param password
     * @return an authenticated docker client if credentials are provided
     * @throws IllegalArgumentException if registry address is blank
     * @throws DockerException if authentication fails
     */
    private static DockerClient createClient(String registryAddress, String username,
            String password) throws DockerException, IllegalArgumentException {
        if (StringUtils.isBlank(registryAddress)) {
            throw new IllegalArgumentException("Registry address must not be blank");
        }
        boolean shouldAuthenticate = StringUtils.isNotBlank(username)
                && StringUtils.isNotBlank(password);
        DefaultDockerClientConfig.Builder configBuilder =
                DefaultDockerClientConfig.createDefaultConfigBuilder()
                        .withDockerTlsVerify(false)
                        .withRegistryUrl(registryAddress);
        if (shouldAuthenticate) {
            configBuilder.withRegistryUsername(username)
                    .withRegistryPassword(password);
        }
        DefaultDockerClientConfig config = configBuilder.build();
        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();
        DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);
        if (shouldAuthenticate) {
            dockerClient.authCmd().exec();
            log.info("Authenticated Docker client registry [registry:{}, username:{}]",
                    registryAddress, username);
        }
        return dockerClient;
    }

    /**
     * Parse Docker image name and its registry address. If no registry is specified
     * the default Docker registry {@link DockerClientInstance#DEFAULT_DOCKER_REGISTRY}
     * is returned.
     * <p>
     * e.g.:
     * host.xyz/image:tag           - host.xyz
     * username/image:tag           - docker.io
     * docker.io/username/image:tag - docker.io
     *
     * @param imageName name of the docker image
     * @return registry address
     */
    public static String parseRegistryAddress(String imageName) {
        NameParser.ReposTag reposTag = NameParser.parseRepositoryTag(imageName);
        NameParser.HostnameReposName hostnameReposName = NameParser.resolveRepositoryName(reposTag.repos);
        String registry = hostnameReposName.hostname;
        return AuthConfig.DEFAULT_SERVER_ADDRESS.equals(registry)
                // to be consistent, we use common default address
                // everywhere for the default DockerHub registry
                ? DockerClientInstance.DEFAULT_DOCKER_REGISTRY
                : registry;
    }

    static class FrameResultCallback extends ResultCallback.Adapter<Frame> {
        private final StringBuilder stdout = new StringBuilder();
        private final StringBuilder stderr = new StringBuilder();

        public String getStdout() {
            return stdout.toString();
        }

        public String getStderr() {
            return stderr.toString();
        }

        @Override
        public void onNext(Frame object) {
            if (object.getStreamType() == StreamType.STDOUT) {
                stdout.append(new String(object.getPayload()));
            } else if (object.getStreamType() == StreamType.STDERR) {
                stderr.append(new String(object.getPayload()));
            }
        }
    }

}
