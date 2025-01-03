/*
 * Copyright 2022-2025 IEXEC BLOCKCHAIN TECH
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

import jakarta.annotation.Nonnull;
import lombok.Getter;


/**
 * List of supported SGX drivers and devices.
 * <p>
 * Currently, 3 modes are supported:
 * <ul>
 * <li>{@code NONE} for no device, the container will not be running in an enclave.
 * <li>{@code LEGACY} for {@code /dev/isgx} device. This device is created by installing manually the SGX driver in a
 *     Linux kernel prior to version 5.11.
 * <li>{@code NATIVE} for {@code /dev/sgx/enclave} and {@code /dev/sgx/provision} devices. Those devices are created
 *     automatically on compatible hardware where SGX is supported and kernel version is greater than or equal to 5.11.
 * </ul>
 * <p>
 * Since kernel version 5.11, official devices are {@code /dev/sgx_enclave} and {@code /dev/sgx_provision}.
 * It is not possible to upgrade the {@code NATIVE} driver mode with those devices as we use an old version of the
 * Gramine framework which does not support them. An upgrade to a newer version of the Gramine framework is required
 * before updating this enum.
 *
 * @see <a href="https://github.com/gramineproject/gramine/blob/2ad54dd52426da115261a26244c10110840f9c83/tools/sgx/is-sgx-available/is_sgx_available.cpp#L172">
 * Gramine SGX drivers support</a>
 */
@Getter
public enum SgxDriverMode {
    NONE(),
    LEGACY("/dev/isgx"),
    NATIVE("/dev/sgx/enclave", "/dev/sgx/provision");

    private final String[] devices;

    SgxDriverMode(String... driverNames) {
        this.devices = driverNames;
    }

    /**
     * Returns {@literal false} if given {@link SgxDriverMode} is {@literal null}
     * or {@link SgxDriverMode#NONE}, {@literal true} otherwise.
     *
     * @param driverMode {@link SgxDriverMode} object to check.
     * @return {@literal false} if given {@link SgxDriverMode} is {@literal null}
     * or {@link SgxDriverMode#NONE}, {@literal true} otherwise.
     */
    public static boolean isDriverModeNotNone(@Nonnull SgxDriverMode driverMode) {
        return driverMode != NONE;
    }
}
