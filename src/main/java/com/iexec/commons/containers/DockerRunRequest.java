/*
 * Copyright 2023-2025 IEXEC BLOCKCHAIN TECH
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

package com.iexec.commons.containers;

import com.github.dockerjava.api.model.HostConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DockerRunRequest {

    @Builder.Default
    private HostConfig hostConfig = HostConfig.newHostConfig();
    private String chainTaskId;
    private String containerName;
    private int containerPort;
    private String imageUri;
    private String entrypoint;
    private String cmd;
    private List<String> env;
    private long maxExecutionTime;
    private SgxDriverMode sgxDriverMode;
    private String workingDir;
    private boolean shouldDisplayLogs;

    public String getStringArgsCmd() {
        return this.cmd;
    }

    public String[] getArrayArgsCmd() {
        return ArgsUtils.stringArgsToArrayArgs(this.cmd);
    }

    public SgxDriverMode getSgxDriverMode() {
        return sgxDriverMode != null ? sgxDriverMode : SgxDriverMode.NONE;
    }
}
