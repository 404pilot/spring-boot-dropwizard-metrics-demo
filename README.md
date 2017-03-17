

## 0x01 How `spring` web application is started

1. initializes `spring listener`

   1. `spring listener` creates `spring context`(`WebApplicationContext`/`ApplicationContext`)

   2. `spring context` is injected into `ServletContext`

      ``` java
      // org.springframework.web.context.ContextLoader
      public WebApplicationContext initWebApplicationContext(ServletContext servletContext) {
        // ...
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, this.context);
        // ...
      }
      ```

2. initializes other listeners and servlets

   * they both can access `ServletContext` which means they are able to get `spring context` to get spring beans and other stuff. So eventually, those `servlet`s can be managed by `spring`.

## 0x02 Spring Context -> Servlet Context

When web container starts, it will initialize `listener`s first, then `servlet`s. Those listeners and servlets are not managed by spring, but they will have access to spring context since spring listener will create spring context, and inject spring context into servlet context. 

Normally, with the following

``` xml
<context-param>
    <param-name>contextConfigLocation</param-name>
    <param-value>classpath:applicationContext.xml</param-value>
</context-param>

<listener>
    <listener-class>org.springframework.web.context.ContextLoaderListener</listener-class>
</listener>
```

web container will initialize `org.springframework.web.context.ContextLoaderListener` with some parameters and then inject spring context into servlet context.

``` java
// org.springframework.web.context.ContextLoader
public WebApplicationContext initWebApplicationContext(ServletContext servletContext) {
  // ...
  servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, this.context);
  // ...
}
```

## 0x03 Listeners/Servlets with Spring

* `WebApplicationContextUtils.getRequiredWebApplicationContext(ServletContext sc)`

* Explicitly ask `spring` to initialize itself

  `WebApplicationContextUtils.getRequiredWebApplicationContext(event.getServletContext()).getAutowireCapableBeanFactory().autowireBean(this);`

* Get `spring bean`s manually

  `WebApplicationContextUtils.getWebApplicationContext(sce.getServletContext()).getBean(ConfigService.class);`

* Get bean directly from `ServletContext` which is registered somewhere else

* ...

``` java
package com.ryantenney.metrics.spring.servlets;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.servlets.MetricsServlet;
import com.codahale.metrics.servlets.HealthCheckServlet;

public class MetricsServletsContextListener implements ServletContextListener {

	@Autowired
	private MetricRegistry metricRegistry;

	@Autowired
	private HealthCheckRegistry healthCheckRegistry;

	private final MetricsServletContextListener metricsServletContextListener = new MetricsServletContextListener();
	private final HealthCheckServletContextListener healthCheckServletContextListener = new HealthCheckServletContextListener();

	@Override
	public void contextInitialized(ServletContextEvent event) {
		WebApplicationContextUtils.getRequiredWebApplicationContext(event.getServletContext()).getAutowireCapableBeanFactory().autowireBean(this);

		metricsServletContextListener.contextInitialized(event);
		healthCheckServletContextListener.contextInitialized(event);
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {}

	class MetricsServletContextListener extends MetricsServlet.ContextListener {

		@Override
		protected MetricRegistry getMetricRegistry() {
			return metricRegistry;
		}

	}

	class HealthCheckServletContextListener extends HealthCheckServlet.ContextListener {

		@Override
		protected HealthCheckRegistry getHealthCheckRegistry() {
			return healthCheckRegistry;
		}

	}

}
```

``` java
public class ConfigListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent sce) {   
        ConfigService configService = WebApplicationContextUtils.getWebApplicationContext(sce.getServletContext()).getBean(ConfigService.class);
        configService.initConfig();
    }
 
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}
```

``` java
// com.codahale.metrics.servlets.MetricsServlet
@Override
public void init(ServletConfig config) throws ServletException {
    // ...
    final ServletContext context = config.getServletContext();
  
    if (null == registry) {
        final Object registryAttr = context.getAttribute(METRICS_REGISTRY);
        if (registryAttr instanceof MetricRegistry) {
            this.registry = (MetricRegistry) registryAttr;
        } else {
            throw new ServletException("Couldn't find a MetricRegistry instance.");
        }
    }
    // ...
}
```



## 0x04 metrics-servlets

[http://metrics.dropwizard.io/3.2.1/manual/servlets.html](http://metrics.dropwizard.io/3.2.1/manual/servlets.html)

> You will need to add your `MetricRegistry` and `HealthCheckRegistry` instances to the servlet context as attributes named `com.codahale.metrics.servlets.MetricsServlet.registry` and`com.codahale.metrics.servlets.HealthCheckServlet.registry`, respectively.

or

> You can do this using the Servlet API by extending `MetricsServlet.ContextListener` for MetricRegistry:

```java
// com.codahale.metrics.servlets.MetricsServlet
@Override
public void init(ServletConfig config) throws ServletException {
    super.init(config);

    final ServletContext context = config.getServletContext();
    if (null == registry) {
        final Object registryAttr = context.getAttribute(METRICS_REGISTRY);
        if (registryAttr instanceof MetricRegistry) {
            this.registry = (MetricRegistry) registryAttr;
        } else {
            throw new ServletException("Couldn't find a MetricRegistry instance.");
        }
    }

    final TimeUnit rateUnit = parseTimeUnit(context.getInitParameter(RATE_UNIT),
                                            TimeUnit.SECONDS);
    final TimeUnit durationUnit = parseTimeUnit(context.getInitParameter(DURATION_UNIT),
                                                TimeUnit.SECONDS);
    final boolean showSamples = Boolean.parseBoolean(context.getInitParameter(SHOW_SAMPLES));
    MetricFilter filter = (MetricFilter) context.getAttribute(METRIC_FILTER);
    if (filter == null) {
      filter = MetricFilter.ALL;
    }
    this.mapper = new ObjectMapper().registerModule(new MetricsModule(rateUnit,
                                                                      durationUnit,
                                                                      showSamples,
                                                                      filter));

    this.allowedOrigin = context.getInitParameter(ALLOWED_ORIGIN);
    this.jsonpParamName = context.getInitParameter(CALLBACK_PARAM);
}
```

Basically the only thing we need to do is to **inject** correct `MetricsRegistry` into `ServletContext` with a proper key named `METRICS_REGISTRY`.

### solution 01

``` xml
<!-- Creates a MetricRegistry bean -->
<metrics:metric-registry id="metricRegistry"/>

<!-- Creates a HealthCheckRegistry bean (Optional) -->
<metrics:health-check-registry id="healthCheckRegistry"/>

<bean class="org.springframework.web.context.support.ServletContextAttributeExporter">
    <property name="attributes">
        <map>
            <entry key="com.codahale.metrics.servlets.MetricsServlet.registry"
                   value-ref="metricRegistry"/>
            <entry key="com.codahale.metrics.servlets.HealthCheckServlet.registry"
                   value-ref="healthCheckRegistry"/>
        </map>
    </property>
</bean>
```

Use `ServletContextAttributeExporter` to inject spring beans. In this case, those spring beans are declared in the same xml file, but they can be any spring beans.

### solution 02

``` java
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

    @Bean
    public ServletListenerRegistrationBean registerMetricsServletsContextListener() {
        ServletListenerRegistrationBean<MetricsServletsContextListener> bean = new ServletListenerRegistrationBean<>();
        bean.setListener(new MetricsServletsContextListener());
        return bean;
    }

    @Bean
    public ServletRegistrationBean servletRegistrationBean() {
        // TODO eager load
        return new ServletRegistrationBean(new AdminServlet(), "/metrics/admin/*");
    }
}
```

`spring boot` knows to let web container to initialize listener first by recognizing `ServletListenerRegistrationBean`. ( I guess spring-boot does convert whole project structure to the old way with web.xml underneath, so web container is able to initialize listeners and servlets)

`MetricsServletsContextListener` is included in `metrics-spring` not `metrics-servlets`. 

``` java
package com.ryantenney.metrics.spring.servlets;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.servlets.MetricsServlet;
import com.codahale.metrics.servlets.HealthCheckServlet;

public class MetricsServletsContextListener implements ServletContextListener {

    // bean name is defined here!
	@Autowired
	private MetricRegistry metricRegistry;

	@Autowired
	private HealthCheckRegistry healthCheckRegistry;

	private final MetricsServletContextListener metricsServletContextListener = new MetricsServletContextListener();
	private final HealthCheckServletContextListener healthCheckServletContextListener = new HealthCheckServletContextListener();

	@Override
	public void contextInitialized(ServletContextEvent event) {
		WebApplicationContextUtils.getRequiredWebApplicationContext(event.getServletContext()).getAutowireCapableBeanFactory().autowireBean(this);

		metricsServletContextListener.contextInitialized(event);
		healthCheckServletContextListener.contextInitialized(event);
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {}

	class MetricsServletContextListener extends MetricsServlet.ContextListener {

		@Override
		protected MetricRegistry getMetricRegistry() {
			return metricRegistry;
		}

	}

	class HealthCheckServletContextListener extends HealthCheckServlet.ContextListener {

		@Override
		protected HealthCheckRegistry getHealthCheckRegistry() {
			return healthCheckRegistry;
		}

	}
}
```

`@autowired` can be correctly parsed after `autowireBean(this)` is executed.

### solution 03

``` java
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.servlets.MetricsServlet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MyMetricsServletContextListener extends MetricsServlet.ContextListener {

    @Autowired
    private MetricRegistry metricRegistry;

    @Override
    protected MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }
}
```

`spring-boot` creates this listener with spring-managed `metricRegistry`, and it will call `MetricsServlet.ContextListener.contextInitialized(ServletContextEvent event)`

``` java
@Override
public void contextInitialized(ServletContextEvent event) {
    final ServletContext context = event.getServletContext();
    context.setAttribute(METRICS_REGISTRY, getMetricRegistry());
    context.setAttribute(METRIC_FILTER, getMetricFilter());
    // ...
}
```

`context.setAttribute(METRICS_REGISTRY, getMetricRegistry());` will do the work. But this is kinda weird that the only purpose of this listener is to inject beans into servlet context in a vague way. We don't need a listener to do this.

``` java
// same way
@Configuration
@ImportResource("classpath:metrics-spring.xml")
public class MetricSpringConfig {
    @Autowired
    private HealthCheckRegistry healthCheckRegistry;

    @Autowired
    private MetricRegistry metricRegistry;

    @Bean
    public ServletRegistrationBean servletRegistrationBean() {
        return new ServletRegistrationBean(new AdminServlet(), "/metrics/admin/*");
    }

    @Bean
    public HealthCheckServlet.ContextListener healthCheckListener() {
        return new HealthCheckServlet.ContextListener() {
            @Override
            protected HealthCheckRegistry getHealthCheckRegistry() {
                return healthCheckRegistry;
            }
        };
    }

    @Bean
    public MetricsServlet.ContextListener metricsListener(){
        return new MetricsServlet.ContextListener(){
            @Override
            protected MetricRegistry getMetricRegistry() {
                return metricRegistry;
            }
        };
    }
}
```

## 0x05 

`@WebListener` is not defined in spring and is part of `servlet 3.0`(javax.servlet.annotation.WebListener). It should behave the same way as it is defined in `web.xml`. 