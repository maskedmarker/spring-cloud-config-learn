# spring config client


## spring-cloud-config-client的spring-factories文件

```text
spring-cloud-config-client依赖的spring-factories文件中的配置

# Auto Configure
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
org.springframework.cloud.config.client.ConfigClientAutoConfiguration


# Bootstrap components
org.springframework.cloud.bootstrap.BootstrapConfiguration=\
org.springframework.cloud.config.client.ConfigServiceBootstrapConfiguration,\
org.springframework.cloud.config.client.DiscoveryClientConfigServiceBootstrapConfiguration
```


## ConfigServiceBootstrapConfiguration

```text
spring-cloud-context通过PropertySourceBootstrapConfiguration引入了一个新的扩展点:即支持从远程加载配置文件.
spring-cloud-starter-config通过向boostrap-context中注入ConfigServicePropertySourceLocator,从而达利用PropertySourceBootstrapConfiguration向application-context的env中注入远程配置.
```

```text
ConfigServiceBootstrapConfiguration引入的ConfigServicePropertySourceLocator/ConfigClientProperties/RetryConfiguration都是工作在boostrap-context,而非application-context.
这里也体现了boostrap-context存在的必要性,这些协助拉取远程配置文件的bean不应该出现在application-context.
```

###  ConfigServicePropertySourceLocator

```text
spring-cloud-config-client使用restTemplate从config-server获取http响应,并根据spring-config协议来解析报文.
```


## DiscoveryClientConfigServiceBootstrapConfiguration

```text
由于从远程获取配置文件需要向目标服务发送http请求,这里存在2种使用场景:
1.目标服务直接用host+port写死,直接调用.
2.目标服务可以通过服务发现来获得.

对于第二种,DiscoveryClientConfigServiceBootstrapConfiguration通过HeartbeatListener来从心跳中提取configClientProperties.discovery.serviceId指定的标识,获取到配置服务的连接地址.
```