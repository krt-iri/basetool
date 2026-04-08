package de.greluc.krt.iri.basetool.frontend;

import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import java.lang.reflect.Method;

public class PrintMethods {
    public static void main(String[] args) {
        for (Method m : CookieLocaleResolver.class.getMethods()) {
            if (m.getName().toLowerCase().contains("cookie")) {
                System.out.println(m.getName() + " : " + m.getParameterTypes().length);
            }
        }
    }
}