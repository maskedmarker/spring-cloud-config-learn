# refresh scope

## GenericScope

```text
public class GenericScope implements Scope, BeanFactoryPostProcessor, BeanDefinitionRegistryPostProcessor, DisposableBean {

    // 由于targetDefinition的scope值非单例和原型模式,所以每次beanFactory().getBean(xxx)都会调用Scope.get()方法
    // objectFactory是beanFactory提供的构建bean的能力,方便scope使用
    public Object get(String name, ObjectFactory<?> objectFactory) {
		BeanLifecycleWrapper value = this.cache.put(name, new BeanLifecycleWrapper(name, objectFactory));             // GenericScope将需要自己管理的bean(以及如何构建bean)收集起来
		this.locks.putIfAbsent(name, new ReentrantReadWriteLock());
		try {
			return value.getBean();                                                                                   // 然会所需bean(必要时通过beanFactory提供的构建bean的能力来实例化一个bean)
		}
		catch (RuntimeException e) {
			this.errors.put(name, e);
			throw e;
		}
	}
	
	
	// scope刷新时
	public void destroy() {
		List<Throwable> errors = new ArrayList<Throwable>();
		Collection<BeanLifecycleWrapper> wrappers = this.cache.clear();
		for (BeanLifecycleWrapper wrapper : wrappers) {                                  // 将自己管理的bean都
			try {
				Lock lock = this.locks.get(wrapper.getName()).writeLock();
				lock.lock();
				try {
					wrapper.destroy();
				}
				finally {
					lock.unlock();
				}
			}
			catch (RuntimeException e) {
				errors.add(e);
			}
		}
		if (!errors.isEmpty()) {
			throw wrapIfNecessary(errors.get(0));
		}
		this.errors.clear();
	}
}
```

### BeanLifecycleWrapper

GenericScope.cache通过使用类BeanLifecycleWrapper,让GenericScope不仅能知道自己在管理哪些bean实例,还知道如何重新创建这些bean实例

```text
private static class BeanLifecycleWrapper {

    private final String name;

    private final ObjectFactory<?> objectFactory;     // beanFactory提供的构建bean的能力(体现了spring在创建bean时的功能内聚性,都收敛到beanFactory这里)

    private Object bean;                              // 原对象,非代理类,scope刷新时会被清理掉,然后再通过objectFactory重新构建

    private Runnable callback;                        // 暂时没有用到


    public Object getBean() {
        if (this.bean == null) {                                       // 之前destroy过(即被scope刷新清理掉了)
            synchronized (this.name) {
                if (this.bean == null) {
                    this.bean = this.objectFactory.getObject();       // 再创建一次
                }
            }
        }
        return this.bean;
    }
    
    // 现在callback没有用到,所以在scope刷新时,仅仅是将target-bean失去强引用就结束了
    public void destroy() {
        if (this.callback == null) {
            return;
        }
        synchronized (this.name) {
            Runnable callback = this.callback;
            if (callback != null) {
                callback.run();
            }
            this.callback = null;
            this.bean = null;
        }
    }
}
```

## RefreshScope



### LockedScopedProxyFactoryBean

目的: 防止scope刷新时,有代码调用用target对象

```text
public static class LockedScopedProxyFactoryBean<S extends GenericScope> extends ScopedProxyFactoryBean implements MethodInterceptor {

    private final S scope;
    private String targetBeanName;
    
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        super.setBeanFactory(beanFactory);
        Object proxy = getObject();
        if (proxy instanceof Advised) {
            Advised advised = (Advised) proxy;
            advised.addAdvice(0, this);
        }
    }
    
    // 目的: 防止scope刷新时,有代码调用用target对象
	public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        //.. 
        
        Object proxy = getObject();
        ReadWriteLock readWriteLock = this.scope.getLock(this.targetBeanName);
        Lock lock = readWriteLock.readLock();
        lock.lock();
        try {
            if (proxy instanceof Advised) {
                Advised advised = (Advised) proxy;
                ReflectionUtils.makeAccessible(method);
                return ReflectionUtils.invokeMethod(method,
                        advised.getTargetSource().getTarget(),
                        invocation.getArguments());
            }
            return invocation.proceed();
        }
        catch (UndeclaredThrowableException e) {
            throw e.getUndeclaredThrowable();
        }
        finally {
            lock.unlock();
        }
    }
}
```