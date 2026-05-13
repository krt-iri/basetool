package de.greluc.krt.iri.basetool.frontend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.CssLinkResourceTransformer;
import org.springframework.web.servlet.resource.VersionResourceResolver;

/** Spring configuration for Web Mvc. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/**")
        .addResourceLocations(
            "classpath:/META-INF/resources/",
            "classpath:/resources/",
            "classpath:/static/",
            "classpath:/public/")
        .setCachePeriod(31536000)
        .resourceChain(true)
        .addResolver(new VersionResourceResolver().addContentVersionStrategy("/**"))
        .addTransformer(new CssLinkResourceTransformer());
  }
}
