package de.greluc.krt.iri.basetool.frontend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoResourceFoundException(NoResourceFoundException e, Model model) {
        model.addAttribute("error", "Not Found");
        model.addAttribute("message", "The requested resource could not be found.");
        model.addAttribute("status", "404");
        return "error/error";
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex, Model model, jakarta.servlet.http.HttpServletRequest request) {
        System.err.println("[DEBUG_LOG] Frontend Type mismatch for parameter " + ex.getName() + ": value='" + ex.getValue() + "', targetType=" + ex.getRequiredType() + ", message=" + ex.getMessage());
        if (request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json")) {
            return org.springframework.http.ResponseEntity.badRequest().body("Invalid parameter: " + ex.getName());
        }
        model.addAttribute("error", "Bad Request");
        model.addAttribute("message", "Invalid value for parameter " + ex.getName());
        model.addAttribute("status", "400");
        return "error/error";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleException(Exception e, Model model) {
        String title = "Unexpected Error";
        String message = e.getMessage();
        String status = "500";

        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof WebClientResponseException wcre) {
                status = String.valueOf(wcre.getStatusCode().value());
                title = "Request failed";
                String body = wcre.getResponseBodyAsString();
                message = (body != null && !body.isBlank()) ? body : wcre.getMessage();
                break;
            }
            cause = cause.getCause();
        }

        model.addAttribute("error", title);
        model.addAttribute("message", message);
        model.addAttribute("status", status);
        return "error/error";
    }
}
