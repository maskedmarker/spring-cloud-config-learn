# spring-scoped-object


## ClassPathBeanDefinitionScanner

```text
org.springframework.context.annotation.ClassPathBeanDefinitionScanner.doScan


protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
    Set<BeanDefinitionHolder> beanDefinitions = new LinkedHashSet<>();
    for (String basePackage : basePackages) {
        Set<BeanDefinition> candidates = findCandidateComponents(basePackage);                                                          // 携带@Component的类
        for (BeanDefinition candidate : candidates) {
            ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(candidate);                                   // 从类型中获取scope信息
            candidate.setScope(scopeMetadata.getScopeName());
            String beanName = this.beanNameGenerator.generateBeanName(candidate, this.registry);
            if (candidate instanceof AbstractBeanDefinition) {
                postProcessBeanDefinition((AbstractBeanDefinition) candidate, beanName);                                                 // 设置beanDefinition默认值
            }
            if (candidate instanceof AnnotatedBeanDefinition) {
                AnnotationConfigUtils.processCommonDefinitionAnnotations((AnnotatedBeanDefinition) candidate);                           // 提取信息设置beanDefinition属性值
            }
            if (checkCandidate(beanName, candidate)) {
                BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(candidate, beanName);
                definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);           // 生成代理beanDefinition(原beanDefinition偷偷换个名字注册)
                beanDefinitions.add(definitionHolder);
                registerBeanDefinition(definitionHolder, this.registry);                                                                 // 将proxy的beanDefinition以原beanName注册
            }
        }
    }
    return beanDefinitions;
}
```
## ScopedProxyUtils

生产代理对象的proxyDefinition的scope是单例模式,而targetDefinition的scope依然保持某个scope-name值🎯🎯🎯💯💯💯💯
这里用户代码使用代理对象保持稳定的关系,理对象于原对象之间可以保持变化. 从而通过代理对象这个中间人,达到了刷新target对象.

```text
public abstract class ScopedProxyUtils {

    private static final String TARGET_NAME_PREFIX = "scopedTarget.";
    
	public static String getTargetBeanName(String originalBeanName) {
		return TARGET_NAME_PREFIX + originalBeanName;
	}
	
	
	// 假设了caller会自己将proxyDefinition注册到registry 🎯
	public static BeanDefinitionHolder createScopedProxy(BeanDefinitionHolder definition, BeanDefinitionRegistry registry, boolean proxyTargetClass) {

		String originalBeanName = definition.getBeanName();
		BeanDefinition targetDefinition = definition.getBeanDefinition();
		String targetBeanName = getTargetBeanName(originalBeanName);                                                       // 注意这里增加的前缀"scopedTarget." 🎯

		// Create a scoped proxy definition for the original bean name,
		// "hiding" the target bean in an internal target definition.
		RootBeanDefinition proxyDefinition = new RootBeanDefinition(ScopedProxyFactoryBean.class);                         // proxy-bean实例是以FactoryBean模式产生的,这样同时支持单例和原型模式 🎯🎯🎯
		proxyDefinition.setDecoratedDefinition(new BeanDefinitionHolder(targetDefinition, targetBeanName));                // proxyDefinition还是持有targetDefinition的信息,知道proxy要decorate谁 🎯🎯🎯
		proxyDefinition.setOriginatingBeanDefinition(targetDefinition);
		proxyDefinition.setSource(definition.getSource());
		proxyDefinition.setRole(targetDefinition.getRole());

		proxyDefinition.getPropertyValues().add("targetBeanName", targetBeanName);                                         // ScopedProxyFactoryBean的targetBeanName属性值是携带"scopedTarget."的
		if (proxyTargetClass) {
			targetDefinition.setAttribute(AutoProxyUtils.PRESERVE_TARGET_CLASS_ATTRIBUTE, Boolean.TRUE);
			// ScopedProxyFactoryBean's "proxyTargetClass" default is TRUE, so we don't need to set it explicitly here.
		}
		else {
			proxyDefinition.getPropertyValues().add("proxyTargetClass", Boolean.FALSE);
		}

		// Copy autowire settings from original bean definition.
		proxyDefinition.setAutowireCandidate(targetDefinition.isAutowireCandidate());
		proxyDefinition.setPrimary(targetDefinition.isPrimary());
		if (targetDefinition instanceof AbstractBeanDefinition) {
			proxyDefinition.copyQualifiersFrom((AbstractBeanDefinition) targetDefinition);
		}

		// The target bean should be ignored in favor of the scoped proxy.
		targetDefinition.setAutowireCandidate(false); // 🎯🎯🎯 Set whether this bean is a candidate for getting autowired into some other bean.Note that this flag is designed to only affect type-based autowiring
		targetDefinition.setPrimary(false);  //🎯
		// Register the target bean as separate bean in the factory.
		registry.registerBeanDefinition(targetBeanName, targetDefinition);
		// 调用本方法的caller会将proxyDefinition以originalBeanName注册到registry,且是primary,从而达到替代的目的.
		// 而targetDefinition则以notPrimary/notAutowireCandidate不参于type-based autowiring,同时"scopedTarget."+originalBeanName的名字存在与registry,又达到了不参于name-based autowiring  🎯🎯🎯💯💯💯💯

		// Return the scoped proxy definition as primary bean definition
		return new BeanDefinitionHolder(proxyDefinition, originalBeanName, definition.getAliases());     // 注意:proxyDefinition的scope是默认值(即单例),并非某个scope-name值,而原targetDefinition的scope依然保持某个scope-name值🎯🎯🎯💯💯💯💯
	}    
}
```

## ScopedProxyFactoryBean

🎯🎯🎯代理工厂类ScopedProxyFactoryBean的实例在beanFactory中是单例模式出现的.

```text
public class ScopedProxyFactoryBean extends ProxyConfig implements FactoryBean<Object>, BeanFactoryAware, AopInfrastructureBean {
    
    private final SimpleBeanTargetSource scopedTargetSource = new SimpleBeanTargetSource();   // 基于targetBeanName,从beanFactory提取targetBean,为每次proxy被调用时提供target
    private String targetBeanName;                                                            // 属性值是携带"scopedTarget."的
    private Object proxy;
    
    public void setTargetBeanName(String targetBeanName) {
		this.targetBeanName = targetBeanName;
		this.scopedTargetSource.setTargetBeanName(targetBeanName);
	}
	
    public Object getObject() {
		if (this.proxy == null) {
			throw new FactoryBeanNotInitializedException();
		}
		return this.proxy;
	}
	
	public Class<?> getObjectType() {
		if (this.proxy != null) {
			return this.proxy.getClass();
		}
		return this.scopedTargetSource.getTargetClass();
	}
	
    public void setBeanFactory(BeanFactory beanFactory) {
		ConfigurableBeanFactory cbf = (ConfigurableBeanFactory) beanFactory;

		this.scopedTargetSource.setBeanFactory(beanFactory);

		ProxyFactory pf = new ProxyFactory();
		pf.copyFrom(this);                                                                                            // ScopedProxyFactoryBean既是配置源ProxyConfig,又是工厂FactoryBean
		pf.setTargetSource(this.scopedTargetSource);                                                                  // 这里设置proxy对象最终将要调用的用户对象.    🎯🎯🎯🎯🎯🎯🎯💯💯💯💯💯💯💯💯💯💯💯💯💯

		Assert.notNull(this.targetBeanName, "Property 'targetBeanName' is required");
		Class<?> beanType = beanFactory.getType(this.targetBeanName);                                                // 获取原类型,
		if (!isProxyTargetClass() || beanType.isInterface() || Modifier.isPrivate(beanType.getModifiers())) {        // 收集原类型的接口信息,proxy类也要实现这些接口
			pf.setInterfaces(ClassUtils.getAllInterfacesForClass(beanType, cbf.getBeanClassLoader()));
		}

		// Add an introduction that implements only the methods on ScopedObject.
		ScopedObject scopedObject = new DefaultScopedObject(cbf, this.scopedTargetSource.getTargetBeanName());       
		pf.addAdvice(new DelegatingIntroductionInterceptor(scopedObject));                                           // 被标注了@Scope的类的实例在IOC容器中需要是ScopedObject,所以proxy类也要实现ScopedObject接口

		// Add the AopInfrastructureBean marker to indicate that the scoped proxy
		// itself is not subject to auto-proxying! Only its target bean is.
		pf.addInterface(AopInfrastructureBean.class);                                                                  // AopInfrastructureBean marker interface, 生成的proxy实例不能被再包一层代理

		this.proxy = pf.getProxy(cbf.getBeanClassLoader());
	}
	
	public ScopedProxyFactoryBean() {
		setProxyTargetClass(true);
	}
}
```

## SimpleBeanTargetSource

每次调用proxy对象时,需要实时判断target对象是那个.
SimpleBeanTargetSource提供了一种方案,那就是基于target-bean的beanName实时从beanFactory查询.

```text
public class SimpleBeanTargetSource extends AbstractBeanFactoryBasedTargetSource {
    
    private String targetBeanName;   // 被设置为原对象的名字,即在beanFactory中beanName携带"scopedTarget."前缀
    
    @Override
	public Object getTarget() throws Exception {
		return getBeanFactory().getBean(getTargetBeanName());            // 每次aop调用时,proxy都会去调用getTarget(),获取最新的target. 这就为scope的刷新提供了机会💯💯💯💯💯💯
	}
}
```

