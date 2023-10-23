/*
 * Copyright 2023-2023 IEXEC BLOCKCHAIN TECH
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class SgxDriverModeTests {
    @Test
    void checkDevices() {
        assertThat(SgxDriverMode.NONE.getDevices()).isEmpty();
        assertThat(SgxDriverMode.LEGACY.getDevices()).containsExactly("/dev/isgx");
        assertThat(SgxDriverMode.NATIVE.getDevices()).containsExactly("/dev/sgx/enclave", "/dev/sgx/provision");
    }
    @Test
    void checkDriverModeIsNone() {
        assertThat(SgxDriverMode.isDriverModeNotNone(SgxDriverMode.NONE)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = SgxDriverMode.class, names = {"LEGACY", "NATIVE"})
    void checkDriverModeIsNotNone(SgxDriverMode sgxDriverMode) {
        assertThat(SgxDriverMode.isDriverModeNotNone(sgxDriverMode)).isTrue();
    }
}
