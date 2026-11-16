package com.wrapzzo.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** The two open pages of "forgot password", for people and for vendors: open to anyone, and they answer the same whoever asks. */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetHttpTest {

    @Autowired private MockMvc mvc;

    private static final String ASK = "{\"email\":\"%s\"}";

    @Test
    void askingForALinkIsOpenAndAnswersTheSameForAnyEmail() throws Exception {
        for (String path : new String[] {"/api/auth/forgotPassword"}) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(ASK.formatted("someone@example.com")))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.message").value("If that account exists, we have sent a link to reset the password"));
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(ASK.formatted("somebody-else@example.org")))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.message").value("If that account exists, we have sent a link to reset the password"));
        }
    }

    @Test
    void aBadEmailIsRefusedAsBadInput() throws Exception {
        mvc.perform(post("/api/auth/forgotPassword").contentType(MediaType.APPLICATION_JSON).content(ASK.formatted("not-an-email"))).andExpect(status().isBadRequest());
    }

    @Test
    void aMadeUpLinkIsRefusedWithAReasonForBothKinds() throws Exception {
        String body = "{\"token\":\"nope\",\"newPassword\":\"a-brand-new-one\",\"confirmPassword\":\"a-brand-new-one\"}";
        for (String path : new String[] {"/api/auth/resetPassword", "/api/vendor/auth/resetPassword"}) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("invalid, expired, or already used")));
        }
    }
}
