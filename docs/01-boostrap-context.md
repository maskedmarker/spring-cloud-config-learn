## bootstrap-context构建

应用启动
```text
org.springframework.boot.SpringApplication.run(java.lang.String...)
    org.springframework.boot.SpringApplication.prepareEnvironment
        org.springframework.boot.SpringApplicationRunListeners.environmentPrepared
            org.springframework.boot.context.event.EventPublishingRunListener.environmentPrepared
                org.springframework.context.event.SimpleApplicationEventMulticaster.multicastEvent(org.springframework.context.ApplicationEvent)
                    org.springframework.context.event.SimpleApplicationEventMulticaster.multicastEvent(org.springframework.context.ApplicationEvent, org.springframework.core.ResolvableType)
                        org.springframework.context.event.SimpleApplicationEventMulticaster.invokeListener
                            org.springframework.context.event.SimpleApplicationEventMulticaster.doInvokeListener
                                org.springframework.cloud.bootstrap.BootstrapApplicationListener.onApplicationEvent
```

```text
应用使用的ApplicationContext被称为application-context,而其parent即spring-cloud的context被称为bootstrap-context

spring-cloud-context的spring.factories中
org.springframework.context.ApplicationListener=org.springframework.cloud.bootstrap.BootstrapApplicationListener
org.springframework.cloud.bootstrap.BootstrapApplicationListener 
```

## BootstrapApplicationListener

BootstrapApplicationListener首先是被注册到application-context.

```text
public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {                                                     // 这是在启动类装配application-context的environment准备就绪时发出的事件
    ConfigurableEnvironment environment = event.getEnvironment();                                                               // 这是application-context的environment
    if (!environment.getProperty("spring.cloud.bootstrap.enabled", Boolean.class, true)) {
        return;
    }
    
    // don't listen to events in a bootstrap context
    // bootstrap-context也是用的SpringApplicationBuilder来构建context的,所以也会受到spring-cloud-context的spring.factories影响,
    // 即bootstrap-context的environment准备就绪时发出的事件也会被BootstrapApplicationListener接收到.
    // BootstrapApplicationListener只处理application-context的事件
    // bootstrap-context在装配阶段中,主动往env中添加了名为bootstrap的PropertySource,启动完成后,又从env中移除了,bootstrap.yml文件对应的是名为applicationConfig: [classpath:/bootstrap.yml]的OriginTrackedMapPropertySource
    if (environment.getPropertySources().contains(BOOTSTRAP_PROPERTY_SOURCE_NAME)) {
        return;
    }
    
    
    
    ConfigurableApplicationContext context = null;
    String configName = environment.resolvePlaceholders("${spring.cloud.bootstrap.name:bootstrap}");                             // 如果没有为bootstrap-context起名字,就是用默认值bootstrap
    
    for (ApplicationContextInitializer<?> initializer : event.getSpringApplication().getInitializers()) {
        if (initializer instanceof ParentContextApplicationContextInitializer) {
            context = findBootstrapContext((ParentContextApplicationContextInitializer) initializer, configName);                // 如果application-context指定了parent,那么就不用自己构建bootstrap-context
        }
    }
    if (context == null) {
        context = bootstrapServiceContext(environment, event.getSpringApplication(), configName);                                // 在application-context的环境env准备好后且未实例化context前,抢先一步将bootstrap-context构建好
        event.getSpringApplication()
                .addListeners(new CloseContextOnFailureApplicationListener(context));                                            // 在application-context注册一个监听器,如果application-context关闭的话,需要将bootstrap-context也关闭
    }

    apply(context, event.getSpringApplication(), environment);
}
```

```text
private ConfigurableApplicationContext bootstrapServiceContext(ConfigurableEnvironment environment, final SpringApplication application, String configName) {
    StandardEnvironment bootstrapEnvironment = new StandardEnvironment();
    MutablePropertySources bootstrapProperties = bootstrapEnvironment.getPropertySources();
    for (PropertySource<?> source : bootstrapProperties) {                                                                        // StandardEnvironment中默认包含了systemProperties和systemEnvironment,因为application-context已经包含了,所以需要剔除
        bootstrapProperties.remove(source.getName());
    }
    
    
    String configLocation = environment.resolvePlaceholders("${spring.cloud.bootstrap.location:}");                               // 查看application-context是否配置了
    String configAdditionalLocation = environment
            .resolvePlaceholders("${spring.cloud.bootstrap.additional-location:}");
    
    Map<String, Object> bootstrapMap = new HashMap<>();
    bootstrapMap.put("spring.config.name", configName);
    // if an app (or test) uses spring.main.web-application-type=reactive, bootstrap will fail
    // force the environment to use none, because if though it is set below in the builder the environment overrides it
    bootstrapMap.put("spring.main.web-application-type", "none");
    if (StringUtils.hasText(configLocation)) {
        bootstrapMap.put("spring.config.location", configLocation);
    }
    if (StringUtils.hasText(configAdditionalLocation)) {
        bootstrapMap.put("spring.config.additional-location",
                configAdditionalLocation);
    }
    bootstrapProperties.addFirst(new MapPropertySource(BOOTSTRAP_PROPERTY_SOURCE_NAME, bootstrapMap));
    for (PropertySource<?> source : environment.getPropertySources()) {
        if (source instanceof StubPropertySource) {
            continue;
        }
        bootstrapProperties.addLast(source);
    }
    // TODO: is it possible or sensible to share a ResourceLoader?
    SpringApplicationBuilder builder = new SpringApplicationBuilder()
            .profiles(environment.getActiveProfiles()).bannerMode(Mode.OFF)
            .environment(bootstrapEnvironment)
            // Don't use the default properties in this builder
            .registerShutdownHook(false).logStartupInfo(false)
            .web(WebApplicationType.NONE);
    final SpringApplication builderApplication = builder.application();
    if (builderApplication.getMainApplicationClass() == null) {
        // gh_425:
        // SpringApplication cannot deduce the MainApplicationClass here
        // if it is booted from SpringBootServletInitializer due to the
        // absense of the "main" method in stackTraces.
        // But luckily this method's second parameter "application" here
        // carries the real MainApplicationClass which has been explicitly
        // set by SpringBootServletInitializer itself already.
        builder.main(application.getMainApplicationClass());
    }
    if (environment.getPropertySources().contains("refreshArgs")) {
        // If we are doing a context refresh, really we only want to refresh the
        // Environment, and there are some toxic listeners (like the
        // LoggingApplicationListener) that affect global static state, so we need a
        // way to switch those off.
        builderApplication
                .setListeners(filterListeners(builderApplication.getListeners()));
    }
    
    // bootstrap-context基于BootstrapImportSelectorConfiguration的配置信息(其实spring-factories的配置也同样生效,因为被SpringApplication类写死了) 🎯🎯🎯
    // bootstrap-context没有像@SpringBootApplication那样启用了@EnableAutoConfiguration和@ComponentScan,所以context中注册的bean除了spring内部必须的提供基础功能的实例之外就剩BootstrapImportSelectorConfiguration引入的实例 🎯🎯🎯🎯🎯🎯💯💯💯💯💯💯💯💯💯
    // 主要引入的是spring-factories文件中寻找key是BootstrapConfiguration的配置🎯🎯🎯
    builder.sources(BootstrapImportSelectorConfiguration.class);                                                                   
    final ConfigurableApplicationContext context = builder.run();                                                                  // 构建完bootstrap-context
    
    // gh-214 using spring.application.name=bootstrap to set the context id via
    // `ContextIdApplicationContextInitializer` prevents apps from getting the actual
    // spring.application.name during the bootstrap phase.
    context.setId("bootstrap");
    
    // Make the bootstrap context a parent of the app context
    // 为application-context的SpringApplication手动添加一个AncestorInitializer,这样application-context的parent会被设置为bootstrap-context
    // 注意SpringApplication不同于ConfigurableApplicationContext,前者是一个启动类,后者是前者要启动的目标.
    // AncestorInitializer没有被配置到spring-factories文件中,是此时因为bootstrap-context需要才临时添加到启动类的配置中.💯💯
    addAncestorInitializer(application, context);
    
    // It only has properties in it now that we don't want in the parent so remove
    // it (and it will be added back later)
    bootstrapProperties.remove(BOOTSTRAP_PROPERTY_SOURCE_NAME);
    
    mergeDefaultProperties(environment.getPropertySources(), bootstrapProperties);                                                  // 把bootstrap-context中env数据merge到application-context的env
    
    
    return context;
}
```

## BootstrapImportSelector


```text
@Configuration(proxyBeanMethods = false)
@Import(BootstrapImportSelector.class)                // 由BootstrapImportSelector决定引入哪些实例
public class BootstrapImportSelectorConfiguration {

}


public class BootstrapImportSelector implements EnvironmentAware, DeferredImportSelector {

	public String[] selectImports(AnnotationMetadata annotationMetadata) {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		
		// Use names and ensure unique to protect against duplicates
		List<String> names = new ArrayList<>(SpringFactoriesLoader.loadFactoryNames(BootstrapConfiguration.class, classLoader));    // 在spring-factories文件中寻找key是BootstrapConfiguration的配置
		
		names.addAll(Arrays.asList(StringUtils.commaDelimitedListToStringArray(
				this.environment.getProperty("spring.cloud.bootstrap.sources", ""))));                                              // 用户也可以通过配置文件向bootstrap-context添加自己的bean

		// ... 
	}
}
```


```text
PropertySourceBootstrapConfiguration implements ApplicationContextInitializer<ConfigurableApplicationContext>

ApplicationContextInitializer如果想要被启动类使用,就需要被添加到spring-factories文件中,且key必须是org.springframework.context.ApplicationContextInitializer,否则不会被启动类使用.
PropertySourceBootstrapConfiguration在spring-factories文件中的key是org.springframework.cloud.bootstrap.BootstrapConfiguration

PropertySourceBootstrapConfiguration是spring-cloud为application-context准备的,而非boostrap-context(原因看后面解释).
spring-cloud-context的spring-factories文件中新增的ApplicationContextInitializer,是bootstrap-context为application-context扩展了PropertySource的来源,PropertySourceLocator支持从远程获取配置文件.
最终,application-context中多了远程配置

```

```text
AncestorInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext>

AncestorInitializer在application-context的env准备阶段的末尾(刚准备好)被BootstrapApplicationListener放到了放到了application-context的启动类SpringApplication中,在application-context的context实例化后的初始化阶段AncestorInitializer开始发挥作用.

public void initialize(ConfigurableApplicationContext context) {
    while (context.getParent() != null && context.getParent() != context) {
        context = (ConfigurableApplicationContext) context.getParent();
    }
    reorderSources(context.getEnvironment());
    new ParentContextApplicationContextInitializer(this.parent).initialize(context);
    // 在application-context中注册一个listener,当其发布ContextRefreshedEvent事件时,接收并随手向application-context发布ParentContextAvailableEvent事件
    // 标准的SpringApplication启动中是支持ParentContextCloserApplicationListener的,
    // 当收到ParentContextAvailableEvent时,为ParentContextCloserApplicationListener的context设置为application-context,并在application-context的parent上新增一个用来监听child关闭的ContextCloserListener,这样当child关闭时也关闭parent
}
```


```text
private void apply(ConfigurableApplicationContext context, SpringApplication application, ConfigurableEnvironment environment) {
    // context是bootstrap-context, application是application-context的启动类, environment是application-context的ApplicationEnvironmentPreparedEvent事件
    // BootstrapMarkerConfiguration作为一个触发标识使用,防止多次触发apply方法
    if (application.getAllSources().contains(BootstrapMarkerConfiguration.class)) {
        return;
    }
    application.addPrimarySources(Arrays.asList(BootstrapMarkerConfiguration.class));
    
    
    // 将bootstrap-context中的ApplicationContextInitializer也应用到application-context
    Set target = new LinkedHashSet<>(application.getInitializers());
    target.addAll(getOrderedBeansOfType(context, ApplicationContextInitializer.class));
    application.setInitializers(target);
    
    addBootstrapDecryptInitializer(application);      // 处理解密的(先忽略)
}

设计意图: ApplicationContextInitializer都是配在spring-factories文件供启动类使用.如果bootstrap-context出现了,那就是为application-context的启动类准备的. 🎯🎯🎯🎯🎯🎯
```


## PropertySourceLocator

```text
spring-cloud-context通过PropertySourceBootstrapConfiguration引入了一个新的扩展点:即支持从远程加载配置文件.

PropertySourceLocator: Strategy for locating (possibly remote) property sources for the Environment. 

在启动类SpringApplication的启动阶段,为application-context已经创建了空的context(还未加载beanDefinition),此时PropertySourceBootstrapConfiguration作为ApplicationContextInitializer为env添加远程配置.
```




## spring-cloud-context引入的spring-factories配置

```text
spring-cloud-context依赖的spring-factories文件中的配置

# AutoConfiguration                                                                                                               // 在application-context中会引入如下这些类的实例,但是在bootstrap-context是会忽略这些自动装配的🎯🎯
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\                                                                  // bootstrap-context将构造SpringApplicationBuilder的primarySources不携带@EnableAutoConfiguration,所以自动装配无法起作用
org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration,\
org.springframework.cloud.autoconfigure.LifecycleMvcEndpointAutoConfiguration,\
org.springframework.cloud.autoconfigure.RefreshAutoConfiguration,\
org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration,\
org.springframework.cloud.autoconfigure.WritableEnvironmentEndpointAutoConfiguration

# Application Listeners                                                                                                           // bootstrap-context也做不到忽视这些ApplicationListener,因为被SpringApplication写死了
org.springframework.context.ApplicationListener=\
org.springframework.cloud.bootstrap.BootstrapApplicationListener,\                                                                // application-context和bootstrap-context中都有,主要是为了处理application-context的事件,所以要防止误处理bootstrap-context的事件(这个类也是构建bootstrap-context的起点) 🎯🎯🎯🎯🎯🎯
org.springframework.cloud.bootstrap.LoggingSystemShutdownListener,\
org.springframework.cloud.context.restart.RestartListener


# Bootstrap components                                                                                                            // 这是为boostrap-context的构建准备的,同时普通的启动
org.springframework.cloud.bootstrap.BootstrapConfiguration=\                                                                      // bootstrap-context将构造SpringApplicationBuilder的sources交给spring-factories文件中key为BootstrapConfiguration的配置来决定
org.springframework.cloud.bootstrap.config.PropertySourceBootstrapConfiguration,\                                                 // spring-factories文件中的key不是org.springframework.context.ApplicationContextInitializer,导致bootstrap-context的启动类无法使用.只会被放入到bootstrap-context中,后续会主动配置到application-context的启动类.
org.springframework.cloud.bootstrap.encrypt.EncryptionBootstrapConfiguration,\
org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration,\
org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration,\
org.springframework.cloud.util.random.CachedRandomPropertySourceAutoConfiguration
```


