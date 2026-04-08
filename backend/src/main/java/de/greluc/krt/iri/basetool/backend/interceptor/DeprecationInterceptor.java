package de.greluc.krt.iri.basetool.backend.interceptor;

import de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Slf4j
@Component
public class DeprecationInterceptor implements HandlerInterceptor {

    private static final DateTimeFormatter HTTP_DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            ApiDeprecation deprecation = handlerMethod.getMethodAnnotation(ApiDeprecation.class);
            boolean isDeprecated = handlerMethod.hasMethodAnnotation(Deprecated.class);

            if (deprecation == null) {
                deprecation = handlerMethod.getBeanType().getAnnotation(ApiDeprecation.class);
            }
            if (!isDeprecated) {
                isDeprecated = handlerMethod.getBeanType().isAnnotationPresent(Deprecated.class);
            }

            if (isDeprecated || deprecation != null) {
                response.addHeader("Deprecation", "true");

                if (deprecation != null) {
                    if (!deprecation.sunset().isEmpty()) {
                        try {
                            LocalDate sunsetDate = LocalDate.parse(deprecation.sunset());
                            response.addHeader("Sunset", HTTP_DATE_FORMATTER.format(sunsetDate.atStartOfDay()));
                        } catch (DateTimeParseException e) {
                            log.warn("Invalid sunset date format on {}: {}. Expected YYYY-MM-DD", handlerMethod.getMethod().getName(), deprecation.sunset());
                        }
                    }

                    if (!deprecation.replacement().isEmpty()) {
                        response.addHeader("Link", "<" + deprecation.replacement() + ">; rel=\"alternate\"");
                    }
                }
            }
        }
        return true;
    }
}
