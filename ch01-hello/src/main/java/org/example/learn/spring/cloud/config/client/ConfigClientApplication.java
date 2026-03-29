package org.example.learn.spring.cloud.config.client;

import org.example.learn.spring.cloud.config.client.controller.ConfigController;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;
import java.util.Map;

@SpringBootApplication
public class ConfigClientApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext applicationContext = SpringApplication.run(ConfigClientApplication.class, args);
        System.out.println("Config Client started!");
        ConfigurableListableBeanFactory beanFactory = applicationContext.getBeanFactory();

        show(beanFactory);
        System.out.println("=====================================================================");
        show((ConfigurableListableBeanFactory) beanFactory.getParentBeanFactory());
    }


    private static void show(ConfigurableListableBeanFactory beanFactory) {
        System.out.println("-------------------------- getSingletonNames ----------------------------------");
        Arrays.stream(beanFactory.getSingletonNames()).forEach(System.out::println);


        System.out.println("------------------------- getBeansOfType -----------------------------------");
        Map<String, ConfigController> beansOfType = beanFactory.getBeansOfType(ConfigController.class);
        // ConfigController实例和代理实例都在application-context中,ConfigController实例的beanName是scopedTarget.configController,而代理实例的beanName是configController
        beansOfType.entrySet().stream().forEach(e -> {
            String beanName = e.getKey();
            ConfigController bean = e.getValue();
            System.out.println(beanName + " -> " + bean.getClass());
        });

        Object configController = beanFactory.getBean("configController");
        System.out.println("configController = " + configController.getClass());
        Object scopedTargetConfigController = beanFactory.getBean("scopedTarget.configController");
        System.out.println("scopedTargetConfigController = " + scopedTargetConfigController.getClass());
        Arrays.stream(configController.getClass().getDeclaredFields()).forEach(System.out::println);

        System.out.println("------------------------------------------------------------");
    }
}