package com.niatpaceya.demo.metricsconfig;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.servlets.AdminServlet;
import com.ryantenney.metrics.spring.servlets.MetricsServletsContextListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

@Configuration
@ImportResource("classpath:metrics-spring.xml")
public class MetricSpringConfig {
    @Autowired
    private HealthCheckRegistry healthCheckRegistry;

    @Autowired
    private MetricRegistry metricRegistry;

    // another way to interact metrics-servlets with spring
    //@Bean
    //public ServletListenerRegistrationBean registerMetricsServletsContextListener() {
    //    ServletListenerRegistrationBean<MetricsServletsContextListener> bean = new ServletListenerRegistrationBean<>();
    //    bean.setListener(new MetricsServletsContextListener());
    //    return bean;
    //}

    @Bean
    public ServletRegistrationBean servletRegistrationBean() {
        // TODO eager load
        return new ServletRegistrationBean(new AdminServlet(), "/metrics/admin/*");
    }
}
