package com.wrapzzo.backend.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @SuppressWarnings("unused")
    private void endpoint(Object body) {}

    private MethodArgumentNotValidException invalid(FieldError... errors) throws Exception {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "request");
        for (FieldError error : errors) result.addError(error);
        Method method = getClass().getDeclaredMethod("endpoint", Object.class);
        return new MethodArgumentNotValidException(new MethodParameter(method, 0), result);
    }

    @Test
    void validationFailureNamesEveryFieldAndReason() throws Exception {
        ResponseEntity<Map<String, Object>> response = handler.handleMethodArgumentNotValid(invalid(
                new FieldError("request", "name", "must not be blank"),
                new FieldError("request", "contactEmail", "must be a well-formed email address")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error"))
                .isEqualTo("Validation failed: name must not be blank; contact email must be a well-formed email address.");
        assertThat((List<?>) response.getBody().get("details")).hasSize(2);
    }

    @Test
    void aRowInASheetIsCountedFromOne() throws Exception {
        ResponseEntity<Map<String, Object>> response = handler.handleMethodArgumentNotValid(
                invalid(new FieldError("request", "rows[2].email", "must be a well-formed email address")));

        assertThat(response.getBody().get("error")).isEqualTo("Validation failed: row 3 email must be a well-formed email address.");
    }

    @Test
    void anUnreadableBodyAndAMissingParameterSayWhatIsWrongToo() {
        assertThat(handler.handleUnreadableBody(null).getBody().get("error").toString()).startsWith("Validation failed: the request body");
        assertThat(handler.handleMissingParameter(new MissingServletRequestParameterException("companyId", "String")).getBody().get("error"))
                .isEqualTo("Validation failed: company id is required.");
    }
}
