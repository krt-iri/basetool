package de.greluc.krt.iri.basetool.frontend.service;

import lombok.Getter;
import org.springframework.http.ProblemDetail;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Getter
public class BackendServiceException extends RuntimeException {
    private final int statusCode;

    public BackendServiceException(String message, Throwable cause, int statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public String getReadableErrorMessage() {
        if (getCause() instanceof WebClientResponseException wcre) {
            try {
                ProblemDetail pd = wcre.getResponseBodyAs(ProblemDetail.class);
                if (pd != null && pd.getDetail() != null) {
                    return pd.getDetail();
                } else if (pd != null && pd.getTitle() != null) {
                    return pd.getTitle();
                }
            } catch (Exception ignored) {
            }
        }
        return getMessage();
    }

    public String getProblemType() {
        if (getCause() instanceof WebClientResponseException wcre) {
            try {
                ProblemDetail pd = wcre.getResponseBodyAs(ProblemDetail.class);
                if (pd != null && pd.getType() != null) {
                    String type = pd.getType().toString();
                    if (type.contains("/")) {
                        return type.substring(type.lastIndexOf("/") + 1);
                    }
                    return type;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}