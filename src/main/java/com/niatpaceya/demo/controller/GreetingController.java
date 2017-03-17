package com.niatpaceya.demo.controller;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.annotation.Counted;
import com.codahale.metrics.annotation.Timed;
import com.niatpaceya.demo.domain.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.codahale.metrics.MetricRegistry.name;

@RestController
@RequestMapping("/greeting")
public class GreetingController {

    private static final String template = "Hello, %s!";
    private final AtomicLong counter = new AtomicLong();

    private Timer timer;

    @PostConstruct
    public void initTimer() {
        timer = metricRegistry.timer(name(GreetingController.class, "responses"));
    }

    @Autowired
    @Qualifier("metricRegistry")
    MetricRegistry metricRegistry;
    //private final Timer responses = metrics.timer(name(RequestHandler.class, "responses"));

    @RequestMapping("/normal")
    @Timed(name = "normal2")
    @Counted(name = "normal3")
    public Message normal(@RequestParam(value = "name", defaultValue = "World") String name) {
        return new Message(counter.incrementAndGet(), String.format(template, name));
    }

    @RequestMapping("/long-method")
    @Timed(name = "longMethod2")
    @Counted(name = "longMethod3")
    public Message longMethod(@RequestParam(value = "name", defaultValue = "World") String name) throws InterruptedException {
        TimeUnit.SECONDS.sleep(2);
        return new Message(counter.incrementAndGet(), String.format(template, name));
    }

    @RequestMapping("/error-method")
    @Timed(name = "errorMethod2")
    @Counted(name = "errorMethod3")
    public Message errorMethod(@RequestParam(value = "name", defaultValue = "World") String name) throws InterruptedException {
        TimeUnit.SECONDS.sleep(2);

        throw new RuntimeException("error");
    }

    @RequestMapping("/nested-method")
    public Message nestedMethod(@RequestParam(value = "name", defaultValue = "World") String name) throws InterruptedException {
        nest();

        return new Message(counter.incrementAndGet(), String.format(template, name));
    }

    @Timed(name = "nestedMethod2")
    @Counted(name = "nestedMethod3")
    public void nest() throws InterruptedException {
        TimeUnit.SECONDS.sleep(2);
    }

    @RequestMapping("/nested-method2")
    public Message nestedMethod2(@RequestParam(value = "name", defaultValue = "World") String name) throws InterruptedException {
        Timer.Context timerContext = this.timer.time();

        try {
            nest01();
        } finally {
            timerContext.stop();
        }

        return new Message(counter.incrementAndGet(), String.format(template, name));
    }

    private void nest01() throws InterruptedException {
        TimeUnit.SECONDS.sleep(2);
    }
}