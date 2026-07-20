/*
 * Copyright 2026 agwlvssainokuni
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

package cherry.mastermeister.registration.api;

import cherry.mastermeister.common.exception.PasswordPolicyViolationException;
import cherry.mastermeister.common.exception.RegistrationTokenInvalidException;
import cherry.mastermeister.registration.UserRegistrationService;
import cherry.mastermeister.registration.entity.Language;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RegistrationController.class)
@AutoConfigureMockMvc(addFilters = false)
class RegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRegistrationService userRegistrationService;

    @Test
    void startRegistration_returnsOk_onValidRequest() throws Exception {
        mockMvc.perform(post("/api/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"taro@example.com\",\"language\":\"ja\"}"))
                .andExpect(status().isOk());

        verify(userRegistrationService).startRegistration("taro@example.com", Language.ja);
    }

    @Test
    void startRegistration_returnsBadRequest_onInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"language\":\"ja\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(userRegistrationService, org.mockito.Mockito.never()).startRegistration(anyString(), any());
    }

    @Test
    void completeRegistration_returnsOk_onValidRequest() throws Exception {
        when(userRegistrationService.completeRegistration("abc-token", "Taro Yamada", Language.ja, "password123"))
                .thenReturn(1L);

        mockMvc.perform(post("/api/registrations/abc-token/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Taro Yamada\",\"preferredLanguage\":\"ja\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void completeRegistration_returnsBadRequest_whenTokenInvalid() throws Exception {
        when(userRegistrationService.completeRegistration(anyString(), anyString(), any(), anyString()))
                .thenThrow(RegistrationTokenInvalidException.expired());

        mockMvc.perform(post("/api/registrations/expired-token/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Taro Yamada\",\"preferredLanguage\":\"ja\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REGISTRATION_TOKEN_EXPIRED"));
    }

    @Test
    void completeRegistration_returnsBadRequest_whenPasswordCompromised() throws Exception {
        when(userRegistrationService.completeRegistration(anyString(), anyString(), any(), anyString()))
                .thenThrow(PasswordPolicyViolationException.compromised());

        mockMvc.perform(post("/api/registrations/some-token/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Taro Yamada\",\"preferredLanguage\":\"ja\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_COMPROMISED"));
    }
}
