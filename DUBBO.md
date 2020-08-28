---
typora-root-url: assert
---





## Dubbo的高可用

dubbo使用zookeeper作为注册中心时，当注册中心全部宕机后
消费者仍可以使用dubbo暴露的服务
原因是因为服务消费者和服务提供者是有本地通讯缓存的
因此在zk宕机后仍可以通过本地通讯缓存来调用服务

dubbo健壮性的体现
* 监控中心宕机不影响使用，只是丢失了部分采样数据
* 注册中心的数据库宕掉后，注册中心通过缓存提供服务列表查询，但不能注册新服务
* 注册中心对等集群，任意一台宕掉后，自动切换到另一台
* 注册中心全部宕掉后，服务提供者和服务消费者仍可以通过本地缓存通讯
* 服务提供者无状态，任意一台宕掉后，不影响使用
* 服务提供者全部宕掉后，服务消费者应用将无法使用，并无限次重连等待服务提供者恢复

通过这些设计，实现dubbo的高可用，减少不能提供服务的时间



## Dubbo为什么要使用注册中心

当只使用直连的点对点连接方式时：
* 当服务提供者增加节点时，需要修改配置文件
* 当其中一个服务提供者宕机时，服务消费者不能及时感知到，还会往宕机的服务发送请求

使用注册中心的好处：
* 可以对服务做集中管理
* 服务容器负责启动（如SpringBoot容器），加载，运行服务提供者。
* 服务提供者在启动时，向注册中心注册自己提供的服务。
* 服务消费者在启动时，向注册中心订阅自己所需的服务。
* 注册中心返回服务提供者地址列表给消费者，如果有变更，注册中心将基于长连接推送变更数据给消费者。(支持变更推送)
* 服务消费者，从提供者地址列表中，基于软负载均衡算法，选一台提供者进行调用，如果调用失败，再选另一台调用。
* 服务消费者和提供者，在内存中累计调用次数和调用时间，定时每分钟发送一次统计数据到监控中心。





## Dubbo服务降级
Dubbo可以通过服务降级功能临时屏蔽某个出错的非关键服务，并定义降级后的返回策略。
向注册中心写入动态配置覆盖规则：

```java
public class Test{
    public void func(){
        RegistryFactory registryFactory = ExtensionLoader.getExtensionLoader(RegistryFactory.class).getAdaptiveExtension();
        Registry registry = registryFactory.getRegistry(URL.valueOf("zookeeper://10.20.153.10:2181"));
        registry.register(URL.valueOf("override://0.0.0.0/com.foo.BarService?category=configurators&dynamic=false&application=foo&mock=force:return+null"));
    }
}
```



* mock=force:return+null 表示消费方对该服务的方法调用都直接返回 null 值，不发起远程调用。用来屏蔽不重要服务不可用时对调用方的影响。

* mock=fail:return+null 表示消费方对该服务的方法调用在失败后，再返回 null 值，不抛异常。用来容忍不重要服务不稳定时对调用方的影响。

更方便的操作方式，可以直接在dubbo-admin中进行服务屏蔽或容错操作

![](/dubbo-admin.png)



服务屏蔽对应了mock=force:return+null参数的设置
服务容错对应了mock=fail:return+null参数的设置

**这两个操作都是对于消费者来说的**





## Dubbo集群容错
默认的容错机制：**Failover Cluster**
<br>失败自动切换，当出现失败，重试其它服务器。通常用于读操作，但重试会带来更长延迟。可通过 retries="2" 来设置重试次数(不含第一次)。

**Failfast Cluster**
快速失败，只发起一次调用，失败立即报错。通常用于非幂等性的写操作，比如新增记录。



**Failsafe Cluster**
失败自动恢复，后台记录失败请求，定时重发。通常用于消息通知操作。



**Forking Cluster**
并行调用多个服务器，只要一个成功即返回。通常用于实时性要求较高的读操作，但需要浪费更多服务资源。可通过 forks="2" 来设置最大并行数。



**Broadcast Cluster**
广播调用所有提供者，逐个调用，任意一台报错则报错。通常用于通知所有提供者更新缓存或日志等本地资源信息。



集群容错的配置：

服务提供者

```xml
<dubbo:service cluster="failsafe" />
```
服务消费者
```xml
<dubbo:reference cluster="failsafe" />
```







## Dubbo原理

可以说dubbo的核心就是基于对rpc流程的封装和实现，底层使用netty作为网络通信组件，以实现高性能的网络通信。



### RPC调用流程
一次完整的rpc调用流程包括：

1. ==**服务消费者（client）以本地调用的方式调用服务/接口**==
2. client stub接收到调用后负责将被调用的方法、参数等组装成能够进行网络传输的消息体,这部分的操作包括数据封装、序列化以及编码
3. client stub找到服务地址，将消息发送给服务端
4. server stub收到消息后进行解码（数据拆封、反序列化）
5. server stub根据解码结果，通过反射调用本地的服务方法
6. 服务提供者（server）将本地服务执行并将返回结果返回给server stub
7. server stub将返回结果打包成消息（数据封装、序列化）发回给消费方
8. client stub接收到返回消息并将消息进行解码（数据拆封、反序列化）
9. client stub根据解码结果，将最终的返回值返回给服务消费者
10. ==**服务消费者最终得到结果**==

那么在用户感知层面，就只有1和10，而dubbo所做的就是将2~9的所有步骤都封装好，
让这些细节都透明化，让服务的远程调用就好像在调本地方法一样。



### dubbo的框架设计

* service 业务逻辑层，这是提供用户实现自己业务逻辑的
* config 配置层：对外配置接口，以 ServiceConfig, ReferenceConfig 为中心，可以直接初始化配置类，也可以通过 spring 解析配置生成配置类
* proxy 服务代理层：服务接口透明代理，生成服务的客户端 Stub 和服务器端 Skeleton, 以 ServiceProxy 为中心，扩展接口为 ProxyFactory
* registry 注册中心层：封装服务地址的注册与发现，以服务 URL 为中心，扩展接口为 RegistryFactory, Registry, RegistryService
* cluster 路由层：封装多个提供者的路由及负载均衡，并桥接注册中心，以 Invoker 为中心，扩展接口为 Cluster, Directory, Router, LoadBalance
* monitor 监控层：RPC 调用次数和调用时间监控，以 Statistics 为中心，扩展接口为 MonitorFactory, Monitor, MonitorService
* protocol 远程调用层：封装 RPC 调用，以 Invocation, Result 为中心，扩展接口为 Protocol, Invoker, Exporter
* exchange 信息交换层：封装请求响应模式，同步转异步，以 Request, Response 为中心，扩展接口为 Exchanger, ExchangeChannel, ExchangeClient, ExchangeServer
* transport 网络传输层：抽象 mina 和 netty 为统一接口，以 Message 为中心，扩展接口为 Channel, Transporter, Client, Server, Codec
* serialize 数据序列化层：可复用的一些工具，扩展接口为 Serialization, ObjectInput, ObjectOutput, ThreadPool

![](/dubbo-framework.jpg)

其中config~protocol层属于rpc层，exchange~serialize层属于remoting
remoting的transport就是基于netty来实现的





### dubbo配置加载-标签解析
dubbo对标签的解析是基于对spring中BeanDefinitionParser的实现：DubboBeanDefinitionParser

这个类做的事情就是在spring启动时，将配置文件(xxx.xml)中所有配置的属性进行解析，并且为每个标签对应的config类注入配置文件中所配置的值

```java
public class DubboBeanDefinitionParser implements BeanDefinitionParser {
    //....
    public DubboBeanDefinitionParser(Class<?> beanClass, boolean required) {
        this.beanClass = beanClass;
        this.required = required;
    }
    //...
    //标签解析的逻辑实现
    private static BeanDefinition parse(Element element, ParserContext parserContext, Class<?> beanClass, boolean required){
        /*通过不断取得标签（element）的id，然后和beanClass进行比较，为对应的config类配置或注入属性*/
    }
    
}
```
那么parse方法中beanClass的获取是在DubboBeanDefinitionParser构造时就已获得，
所有标签和config对应的规则通过DubboNamespaceHandler类的init方法来设置
```java
public class DubboNamespaceHandler extends NamespaceHandlerSupport {

    static {
        Version.checkDuplicate(DubboNamespaceHandler.class);
    }

    @Override
    public void init() {
        //给dubbo根标签注册对应的配置类
        registerBeanDefinitionParser("application", new DubboBeanDefinitionParser(ApplicationConfig.class, true));
        registerBeanDefinitionParser("module", new DubboBeanDefinitionParser(ModuleConfig.class, true));
        registerBeanDefinitionParser("registry", new DubboBeanDefinitionParser(RegistryConfig.class, true));
        registerBeanDefinitionParser("monitor", new DubboBeanDefinitionParser(MonitorConfig.class, true));
        registerBeanDefinitionParser("provider", new DubboBeanDefinitionParser(ProviderConfig.class, true));
        registerBeanDefinitionParser("consumer", new DubboBeanDefinitionParser(ConsumerConfig.class, true));
        registerBeanDefinitionParser("protocol", new DubboBeanDefinitionParser(ProtocolConfig.class, true));
        registerBeanDefinitionParser("service", new DubboBeanDefinitionParser(ServiceBean.class, true));
        registerBeanDefinitionParser("reference", new DubboBeanDefinitionParser(ReferenceBean.class, false));
        registerBeanDefinitionParser("annotation", new AnnotationBeanDefinitionParser());
    }
}
```





### dubbo服务暴露流程

 Dubbo 服务导出过程始于 Spring 容器发布刷新事件，Dubbo 在接收到事件后，会立即执行服务导出逻辑。整个逻辑大致可分为三个部分，第一部分是前置工作，主要用于检查参数，组装 URL。第二部分是导出服务，包含导出服务到本地 (JVM)，和导出服务到远程两个过程。第三部分是向注册中心注册服务，用于服务发现。 

#### 解析ServiceBean

```java

    public void init() {
        ....
        
        //dubbo在解析ServiceBean时（dubbo:service属性），开始了对服务的暴露
        registerBeanDefinitionParser("service", new DubboBeanDefinitionParser(ServiceBean.class, true));
        
        ....

    }
```

先来看一下ServiceBean的内容：

```java
public class ServiceBean<T> extends ServiceConfig<T> implements InitializingBean, DisposableBean,ApplicationContextAware, ApplicationListener<ContextRefreshedEvent>, BeanNameAware,ApplicationEventPublisherAware {
    
    ....
   @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        SpringExtensionFactory.addApplicationContext(applicationContext);
        supportedApplicationListener = addApplicationListener(applicationContext, this);
    }
    
    /**
    	当spring的ioc容器内所有对象都创建完成之后会回调该方法
    	该方法是ApplicationListener中的方法
    **/
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        /**
        	如果该实例是延迟加载的并且是需要暴露的但是又还未暴露的，进行服务暴露
        **/
        if (isDelay() && !isExported() && !isUnexported()) {
            if (logger.isInfoEnabled()) {
                logger.info("The service ready on spring started. service: " + getInterface());
            }
            //服务暴露的入口方法
            export();
        }
    }
    
    /*核心部分*/
    /**
    	由于ServiceBean实现了InitializingBean接口，那么会在
    	该实例(dubbo:service所配置的类)的属性设置完成后调用该方法
    **/
    @Override
    @SuppressWarnings({"unchecked", "deprecation"})
    public void afterPropertiesSet() throws Exception {
        /**
        	前面这一大串的逻辑都是检查是设置属性的
        	主要是对provider、monitor、registry以及protocol的设置
        **/
        //如果是非延迟启动的，此时开始进行服务暴露
        if (!isDelay()) {
            //服务暴露的入口方法
            export();
        }
    }
    
    @Override
    public void export() {
        //调用父类ServiceConfig的export方法
        super.export();
        // Publish ServiceBeanExportedEvent
        //发布已暴露的服务事件
        publishExportEvent();
    }
    
    
    
}
```



#### 执行父类ServiceConfig的export方法

在ServiceBean解析完成后会调用export方法开始服务暴露，此时会进入到父类ServiceConfig的回调中

```java

public class ServiceConfig<T> extends AbstractServiceConfig {
    //....
    
    public synchronized void export() {
        if (provider != null) {
            if (export == null) {
                export = provider.getExport();
            }
            if (delay == null) {
                delay = provider.getDelay();
            }
        }
        if (export != null && !export) {
            return;
        }

        if (delay != null && delay > 0) {
            delayExportExecutor.schedule(new Runnable() {
                @Override
                public void run() {
                    doExport();
                }
            }, delay, TimeUnit.MILLISECONDS);
        } else {
            //执行服务暴露逻辑
            doExport();
        }
    }
    
    protected synchronized void doExport() {
        /**
        	前面一大串的逻辑又是provider、monitor等属性的校验检查
        **/
        //这里开始进行服务url的暴露
        doExportUrls();
        ProviderModel providerModel = new ProviderModel(getUniqueServiceName(), this, ref);
        ApplicationModel.initProviderModel(getUniqueServiceName(), providerModel);
    }
    
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void doExportUrls() {
        /**
        	这里拿到的就是dubbo:service标签所配置的所有服务的url地址
        	而protocols就是对dubbo:protocol标签解析时所拿到的所有协议配置
        	protocols是一个List<ProtocolConfig>，因为可以通过配置多个端口进行服务集群
        **/
        List<URL> registryURLs = loadRegistries(true);
        //通过for循环将所有配置的协议端口/url进行暴露
        for (ProtocolConfig protocolConfig : protocols) {
            doExportUrlsFor1Protocol(protocolConfig, registryURLs);
        }
    }
    
    private void doExportUrlsFor1Protocol(ProtocolConfig protocolConfig, List<URL> registryURLs) {
        //...
        if (!Constants.SCOPE_NONE.toString().equalsIgnoreCase(scope)) {
            //...
            if(xxx){//...
                
            /**
            	封装执行器，将服务的实现类以及接口都封装到一个Invoker对象中
            	最终又将该Invoker对象封装成一个DelegateProviderMetaDataInvoker对象
            	即在Invoker外层再加一层包装
            	这里的ref就是借口的实现类对象 
            **/
            Invoker<?> invoker = proxyFactory.getInvoker(ref, (Class) interfaceClass, registryURL.addParameterAndEncoded(Constants.EXPORT_KEY, url.toFullString()));
        	DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);
            /**
            	完成执行器的封装后，调用protocol的export方法（协议暴露）
            **/
        	Exporter<?> exporter = protocol.export(wrapperInvoker);
			exporters.add(exporter);
            }
       } else {
            Invoker<?> invoker = proxyFactory.getInvoker(ref, (Class) interfaceClass, url);
            DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);
            /**
            	完成执行器的封装后，调用protocol的export方法（协议暴露）
            **/
            Exporter<?> exporter = protocol.export(wrapperInvoker);
            exporters.add(exporter);
                }
            }
        }
        this.urls.add(url);
    }
    
    //....
        
}

```



#### 执行RegistryProtocol类的export方法

再进入到protocol.export方法中，该方法属于Protocol接口中的方法，该接口都多个实现，那么该过程是对服务进行暴露的过程，也就是最终需要将服务注册到注册中心，因此会走到RegistryProtocol.export方法中.

并且在该方法中会完成整个服务暴露工作并将一个DestroyableExporter返回给上层调用（ServiceConfig.epxort）

```java

public class RegistryProtocol implements Protocol {
    
    
    
    //....
    
    public <T> Exporter<T> export(final Invoker<T> originInvoker) throws RpcException {
        //export invoker
        /**
        	将服务执行器进行本地暴露
        	并且开启一个exchangeServer，开始监听服务端口
        	该exchangeServer底层是一个NettyServer实例，也就是网络通讯部分
        	是通过netty实现的
        **/
        final ExporterChangeableWrapper<T> exporter = doLocalExport(originInvoker);

        URL registryUrl = getRegistryUrl(originInvoker);

        //registry provider
        //将服务注册到注册中心
        final Registry registry = getRegistry(originInvoker);
        final URL registeredProviderUrl = getRegisteredProviderUrl(originInvoker);

        //to judge to delay publish whether or not
        //判断是否为延迟发布
        boolean register = registeredProviderUrl.getParameter("register", true);

        /**
        	服务提供者消费者注册表的对provider的注册，
        	将提供者的invoker以及消费者的invoker的信息进行管理并维护到一个
        	concurrentHashMap中，invoker中包含服务的代理对象以及url地址等数据
        	并且在每一次url掉用时都会将对应invoker关系保存起来，相当于本地通讯缓存
        	这也是为什么注册中心都崩了，服务直接仍可以进行通讯
        **/
        ProviderConsumerRegTable.registerProvider(originInvoker, registryUrl, registeredProviderUrl);

        //如果服务不是延迟发布，那么进行服务注册
        if (register) {
            //开始将服务注册到注册中心
            register(registryUrl, registeredProviderUrl);
            ProviderConsumerRegTable.getProviderWrapper(originInvoker).setReg(true);
        }

        // Subscribe the override data
        // FIXME When the provider subscribes, it will affect the scene : a certain JVM exposes the service and call the same service. Because the subscribed is cached key with the name of the service, it causes the subscription information to cover.
        final URL overrideSubscribeUrl = getSubscribedOverrideUrl(registeredProviderUrl);
        final OverrideListener overrideSubscribeListener = new OverrideListener(overrideSubscribeUrl, originInvoker);
        overrideListeners.put(overrideSubscribeUrl, overrideSubscribeListener);
        registry.subscribe(overrideSubscribeUrl, overrideSubscribeListener);
        //Ensure that a new exporter instance is returned every time export
        return new DestroyableExporter<T>(exporter, originInvoker, overrideSubscribeUrl, registeredProviderUrl);
    }
    
    
    //暴露服务执行器（invoker）
    private <T> ExporterChangeableWrapper<T> doLocalExport(final Invoker<T> originInvoker) {
        String key = getCacheKey(originInvoker);
        ExporterChangeableWrapper<T> exporter = (ExporterChangeableWrapper<T>) bounds.get(key);
        if (exporter == null) {
            synchronized (bounds) {
                exporter = (ExporterChangeableWrapper<T>) bounds.get(key);
                if (exporter == null) {
                    final Invoker<?> invokerDelegete = new InvokerDelegete<T>(originInvoker, getProviderUrl(originInvoker));
                    //再次调用protocol.export方法，这一次调用的才是DubboProtocol.export
                    exporter = new ExporterChangeableWrapper<T>((Exporter<T>) 
                               protocol.export(invokerDelegete), originInvoker);
                    bounds.put(key, exporter);
                }
            }
        }
        return exporter;
    }
    
    
    //....
}


```



#### 使用DubboProtocol暴露服务执行器

进入到DubboProtocol.export方法

```java

public class DubboProtocol extends AbstractProtocol {
    //....
    
    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        URL url = invoker.getUrl();

        URL url = invoker.getUrl();

        // export service.
        //服务暴露，通过将其封装到DubboExporter中完成服务暴露
        String key = serviceKey(url);
        DubboExporter<T> exporter = new DubboExporter<T>(invoker, key, exporterMap);
        exporterMap.put(key, exporter);

        //export an stub service for dispatching event
        //如果设置了本地存根，那么也会将该服务进行暴露
        Boolean isStubSupportEvent = url.getParameter(Constants.STUB_EVENT_KEY, Constants.DEFAULT_STUB_EVENT);
        Boolean isCallbackservice = url.getParameter(Constants.IS_CALLBACK_SERVICE, false);
        if (isStubSupportEvent && !isCallbackservice) {
            String stubServiceMethods = url.getParameter(Constants.STUB_EVENT_METHODS_KEY);
            if (stubServiceMethods == null || stubServiceMethods.length() == 0) {
                if (logger.isWarnEnabled()) {
                    logger.warn(new IllegalStateException("consumer [" + url.getParameter(Constants.INTERFACE_KEY) +
                            "], has set stubproxy support event ,but no stub methods founded."));
                }
            } else {
                stubServiceMethodsMap.put(url.getServiceKey(), stubServiceMethods);
            }
        }

        //服务暴露完成后，开始创建并打开进行网络通讯的服务器
        openServer(url);
        optimizeSerialization(url);
        return exporter;
    }

    
    private void openServer(URL url) {
        // find server.
        String key = url.getAddress();
        //client can export a service which's only for server to invoke
        boolean isServer = url.getParameter(Constants.IS_SERVER_KEY, true);
        if (isServer) {
            //创建一个信息交换器服务器，这也是remoting层的顶层实现
            ExchangeServer server = serverMap.get(key);
            if (server == null) {
                //当维护服务的map是空时，创建一个新的服务器
                serverMap.put(key, createServer(url));
            } else {
                // server supports reset, use together with override
                server.reset(url);
            }
        }
    }

    /*信息交换服务器创建*/
    private ExchangeServer createServer(URL url) {
        // send readonly event when server closes, it's enabled by default
        url = url.addParameterIfAbsent(Constants.CHANNEL_READONLYEVENT_SENT_KEY, Boolean.TRUE.toString());
        // enable heartbeat by default
        //...
        ExchangeServer server;
        try {
            //开始进行服务初始化，绑定一个url地址，并且传入一个请求处理器
            server = Exchangers.bind(url, requestHandler);
        } catch (RemotingException e) {
            throw new RpcException("Fail to start server(url: " + url + ") " + e.getMessage(), e);
        }
        //...
        return server;
    }
    
    //....
}

```



#### 开启ExchangeServer并绑定url

服务器地址绑定，此时再进入到Exchanges.bind方法中

```java

public static ExchangeServer bind(URL url, ExchangeHandler handler) throws RemotingException {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler == null");
        }
        url = url.addParameterIfAbsent(Constants.CODEC_KEY, "exchange");
    	
   		//获取一个信息交换服务器，并绑定
        return getExchanger(url).bind(url, handler);
}

```

再次进入该getExchanger(url).bind(url, handler)方法，发现会跳到一个Exchanger接口中，改接口同样有只有一个实现类为HeaderExchanger，进入到该类下的bind方法

```java

@Override
public ExchangeServer bind(URL url, ExchangeHandler handler) throws RemotingException {
    
    /**
    	直到这里，终于看到了transporter层，remoting层的核心即为该层，该层的实现是基于netty的
    **/
    return new HeaderExchangeServer(Transporters.bind(url, new DecodeHandler(new HeaderExchangeHandler(handler))));
}

```



##### 通过传输层进行服务器的url绑定

再次进入到Transporters.bind方法中

```java

public static Server bind(URL url, ChannelHandler... handlers) throws RemotingException {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }
        if (handlers == null || handlers.length == 0) {
            throw new IllegalArgumentException("handlers == null");
        }
        ChannelHandler handler;
        if (handlers.length == 1) {
            handler = handlers[0];
        } else {
            handler = new ChannelHandlerDispatcher(handlers);
        }
    	//获取一个传输器并绑定
        return getTransporter().bind(url, handler);
    }


```

##### 创建并返回NettyServer实例

再次进入到下一层bind方法时，此时进入到了Transporter接口中，改接口有许多实现类，而dubbo的网络通信模块基于netty实现的，因此选择NettyTransporter实现类，最终发现，在这里传回了一个NettyServer实例

```java

public class NettyTransporter implements Transporter {

    public static final String NAME = "netty";

    @Override
    public Server bind(URL url, ChannelHandler listener) throws RemotingException {
        return new NettyServer(url, listener);
    }

    @Override
    public Client connect(URL url, ChannelHandler listener) throws RemotingException {
        return new NettyClient(url, listener);
    }

}

```

那么在NettyServer初始化的过程中，会走到一个方法为NettyServer的doOpen方法，这是打开服务器连接的方法

至此，ExchangeServer服务器启动流程就走完了，通过解析ServiceBean，在装配完属性后，开启一个Exchanger，而这个ExchangeServer又通过底层NettyServer的连接初始化最终完成bind(url,requestHandler)方法，完成绑定。开始进行端口监听，等待服务被调用



#### 回到RegistryProtocol类的export方法中

此时方法层层回调，再回到RegistryProtocol.export里面：

```java

//....

public <T> Exporter<T> export(final Invoker<T> originInvoker) throws RpcException {
        //export invoker
        /**
        	执行服务的本地暴露，暴露传入的Invoker，
        	并且开启一个exchangeServer，为后续的服务注册做准备
        	该exchangeServer底层是一个NettyServer实例，也就是网络通讯部分
        	是通过netty实现的
        **/
        final ExporterChangeableWrapper<T> exporter = doLocalExport(originInvoker);

        URL registryUrl = getRegistryUrl(originInvoker);

        //registry provider
    	/**
    		开启一个zkClient（ CuratorZookeeperClient）
    		并且与远程的zkServer进行连接，注册一个provider服务节点
    		此时只是注册了一个服务的根结点，里面具体的节点信息还未被创建
    		只有在zkClient注册了该服务，后续才可以找到该根结点进行具体的子节点创建
    		其实质就是在zkServer里面创建了一个根文件夹，真正服务节点信息是
    		存储在该文件夹里面的
    	**/
        final Registry registry = getRegistry(originInvoker);
        final URL registeredProviderUrl = getRegisteredProviderUrl(originInvoker);

        //to judge to delay publish whether or not
        //判断是否为延迟发布
        boolean register = registeredProviderUrl.getParameter("register", true);

        ProviderConsumerRegTable.registerProvider(originInvoker, registryUrl, registeredProviderUrl);

        //如果服务不是延迟发布，那么进行服务节点的创建
        if (register) {
            //开始将具体的服务节点创建到zkClient中，进入到下面的register方法里
            register(registryUrl, registeredProviderUrl);
            ProviderConsumerRegTable.getProviderWrapper(originInvoker).setReg(true);
        }

        // Subscribe the override data
        // FIXME When the provider subscribes, it will affect the scene : a certain JVM exposes the service and call the same service. Because the subscribed is cached key with the name of the service, it causes the subscription information to cover.
        final URL overrideSubscribeUrl = getSubscribedOverrideUrl(registeredProviderUrl);
        final OverrideListener overrideSubscribeListener = new OverrideListener(overrideSubscribeUrl, originInvoker);
        overrideListeners.put(overrideSubscribeUrl, overrideSubscribeListener);
        registry.subscribe(overrideSubscribeUrl, overrideSubscribeListener);
        //Ensure that a new exporter instance is returned every time export
        return new DestroyableExporter<T>(exporter, originInvoker, overrideSubscribeUrl, registeredProviderUrl);
    }

/**
     * Get an instance of registry based on the address of invoker
     *
     * @param originInvoker
     * @return
     */
	//获取根据配置的dubbo:registry获取一个注册实例
    private Registry getRegistry(final Invoker<?> originInvoker) {
        //拿出invoker里面的需要被注册的dubbo应用的url
        URL registryUrl = getRegistryUrl(originInvoker);
        //进入RegistryFactory.getRegistry方法
        return registryFactory.getRegistry(registryUrl);
    }


public void register(URL registryUrl, URL registedProviderUrl) {
        Registry registry = registryFactory.getRegistry(registryUrl);
    	//进行服务注册
        registry.register(registedProviderUrl);
}

//....

```



#### 获取注册器实例

RegistryFactory只有一个实现类就是AbstractRegistryFactory，进入该类下的getRegistry方法

```java

public abstract class AbstractRegistryFactory implements RegistryFactory {
    @Override
    public Registry getRegistry(URL url) {
        url = url.setPath(RegistryService.class.getName())
                .addParameter(Constants.INTERFACE_KEY, RegistryService.class.getName())
                .removeParameters(Constants.EXPORT_KEY, Constants.REFER_KEY);
        String key = url.toServiceString();
        // Lock the registry access process to ensure a single instance of the registry
        LOCK.lock();
        try {
            Registry registry = REGISTRIES.get(key);
            if (registry != null) {
                return registry;
            }
            //创建Rregistry实例
            registry = createRegistry(url);
            if (registry == null) {
                throw new IllegalStateException("Can not create registry " + url);
            }
            REGISTRIES.put(key, registry);
            return registry;
        } finally {
            // Release the lock
            LOCK.unlock();
        }
    }
    
    //又遇到了一个抽象方法，继续走实现方法
    protected abstract Registry createRegistry(URL url);
}

```

使用了zk作为注册中心，因此会进入到ZookeeperRegistryFactory.createRegistry方法中

```java

public class ZookeeperRegistryFactory extends AbstractRegistryFactory {

    private ZookeeperTransporter zookeeperTransporter;

    public void setZookeeperTransporter(ZookeeperTransporter zookeeperTransporter) {
        this.zookeeperTransporter = zookeeperTransporter;
    }

    @Override
    public Registry createRegistry(URL url) {
        //初始化zk的注册器实例
        return new ZookeeperRegistry(url, zookeeperTransporter);
    }

}

```

```java

public class ZookeeperRegistry extends FailbackRegistry {

    public ZookeeperRegistry(URL url, ZookeeperTransporter zookeeperTransporter) {
        super(url);
        if (url.isAnyHost()) {
            throw new IllegalStateException("registry address == null");
        }
        String group = url.getParameter(Constants.GROUP_KEY, DEFAULT_ROOT);
        if (!group.startsWith(Constants.PATH_SEPARATOR)) {
            group = Constants.PATH_SEPARATOR + group;
        }
        this.root = group;
        //获取zkClient，并连接zkServer，也就是初始化后zkClient是已连接的
        zkClient = zookeeperTransporter.connect(url);
        zkClient.addStateListener(new StateListener() {
            @Override
            public void stateChanged(int state) {
                if (state == RECONNECTED) {
                    try {
                        recover();
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        });
    }
    
}

```



#### 创建具体的zk客户端对象并返回实例

进入到zookeeperTransporter.connect方法中，跳到的是ZookeeperTransporter接口，此时需要选择实现类，那么这里需要看pom中使用zkClient是那种类型，这里使用的是CuratorZookeeperClient，因此进入到CuratorZookeeperTransporter中

```java

public class CuratorZookeeperTransporter implements ZookeeperTransporter {

    @Override
    public ZookeeperClient connect(URL url) {
        //返回具体的zkClient实例
        return new CuratorZookeeperClient(url);
    }

}

```

```java
public class CuratorZookeeperClient extends AbstractZookeeperClient<CuratorWatcher> {
    
     public CuratorZookeeperClient(URL url) {
        //调用父类构造，将url属性也传递给父类
        super(url);
        try {
            CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                    .connectString(url.getBackupAddress())
                    .retryPolicy(new RetryNTimes(1, 1000))
                    .connectionTimeoutMs(5000);
            String authority = url.getAuthority();
            if (authority != null && authority.length() > 0) {
                builder = builder.authorization("digest", authority.getBytes());
            }
            //客户端创建完成
            client = builder.build();
            //添加相关的监听
            client.getConnectionStateListenable().addListener(new ConnectionStateListener() {
                @Override
                public void stateChanged(CuratorFramework client, ConnectionState state) {
                    if (state == ConnectionState.LOST) {
                        CuratorZookeeperClient.this.stateChanged(StateListener.DISCONNECTED);
                    } else if (state == ConnectionState.CONNECTED) {
                        CuratorZookeeperClient.this.stateChanged(StateListener.CONNECTED);
                    } else if (state == ConnectionState.RECONNECTED) {
                        CuratorZookeeperClient.this.stateChanged(StateListener.RECONNECTED);
                    }
                }
            });
            //启动客户端，连接zkServer
            client.start();
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
    
}

```



#### 进行服务节点创建

完成根服务节点的注册后，开始进行具体服务节点的创建

此时会跳到RegisterService接口中，此时会进入FailbackRegistry.register方法中，但是该方法会先去调用父类的AbstractRegistry.register方法，目的是进行检查，如果没有抛出异常，那么会继续注册方法

```java
public abstract class FailbackRegistry extends AbstractRegistry {
public void register(URL url) {
        super.register(url);
        failedRegistered.remove(url);
        failedUnregistered.remove(url);
        try {
            // Sending a registration request to the server side
            /**
            	向注册中心服务器发送注册请求
            	这也是为什么在DubboProtocol.export执行完服务暴露后还需要去
            	开启一个ExchangeServer了
            **/
            doRegister(url);
        } catch (Exception e) {
            //...
        }
    }
}
```

doRegister是一个抽象方法，当注册中心用的是zk时进入的应该是ZookeeperRegistry.doRegister方法中

```java

public class ZookeeperRegistry extends FailbackRegistry {
     @Override
    protected void doRegister(URL url) {
        try {
            //创建zk客户端以向zkServer发起服务注册
            zkClient.create(toUrlPath(url), url.getParameter(Constants.DYNAMIC_KEY, true));
        } catch (Throwable e) {
            throw new RpcException("Failed to register " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }
}


```

进入AbstractZookeeperClient.create方法中，在该方法中完成所有节点创建

```java

public abstract class AbstractZookeeperClient<TargetChildListener> implements ZookeeperClient {
    
     @Override
    public void create(String path, boolean ephemeral) {
        int i = path.lastIndexOf('/');
        if (i > 0) {
            String parentPath = path.substring(0, i);
            if (!checkExists(parentPath)) {
                create(parentPath, false);
            }
        }
        if (ephemeral) {
            //创建临时节点，临时节点在zk重启后会被清除
            createEphemeral(path);
        } else {
            //创建持久化的节点（这种节点会被保存到zk数据库中）
            createPersistent(path);
        }
    }
    
}

```

服务暴露以及注册、服务节点创建的大概流程如下：

1. 解析dubbo:service标签，即解析ServiceBean，当容器创建完成，触发ContextRefreshedEvent
2. 执行ServiceBean.onApplicationEvent中的export方法
3. 执行父类ServiceConfig中的export()方法
4. 遇到并执行protocol.export方法，进入RegistryProtocol.export中
5. 执行doLocalExport方法，此时再次调用protocol.export方法，本次进入DubboProtocol.export中
6. 执行DubboProtocol中的export()方法
7. 创建一个ExchangeServer并进行url和requestHandler绑定
8. 底层通过Tansporter创建并返回一个NettyServer实例，由NettyServer进行dubbo端口的监听
9. 回到RegistryProtocol.export中，将服务注册到注册中心
10. 如果服务不是延迟发布的，那么通过zkClient在zkServer中创建所有的被暴露的服务节点

总的来说，服务暴露一共干了以下几件事：

- 暴露invoker（provider的执行器）
- 底层通过启动NettyServer，创建并启动一个ExchangeServer，开始监听服务端口（默认是20880）
- 创建zkClient并且向远程的zkServer发起连接，注册provider的服务
- 维护和管理本地注册表，向提供者消费者的本地注册表添加注册信息
- 如果服务非延迟发布，即刻通过zkClient在zkServer中创建所有被暴露的服务节点

官网的服务导出部分的源码导读：https://dubbo.apache.org/zh-cn/docs/source_code_guide/export-service.html





### dubbo服务引用流程

 Dubbo 服务引用的时机有两个，第一个是在 Spring 容器调用 ReferenceBean 的 afterPropertiesSet 方法时引用服务，第二个是在 ReferenceBean 对应的服务被注入到其他类中时引用。这两个引用服务的时机区别在于，第一个是饿汉式的，第二个是懒汉式的。默认情况下，Dubbo 使用懒汉式引用服务。如果需要使用饿汉式，可通过配置 <dubbo:reference> 的 init 属性开启。下面我们按照 Dubbo 默认配置进行分析，整个分析过程从 ReferenceBean 的 getObject 方法开始。当我们的服务被注入到其他类中时，Spring 会第一时间调用 getObject 方法，并由该方法执行服务引用逻辑。按照惯例，在进行具体工作之前，需先进行配置检查与收集工作。接着根据收集到的信息决定服务用的方式，有三种，第一种是引用本地 (JVM) 服务，第二是通过直连方式引用远程服务，第三是通过注册中心引用远程服务。不管是哪种引用方式，最后都会得到一个 Invoker 实例。如果有多个注册中心，多个服务提供者，这个时候会得到一组 Invoker 实例，此时需要通过集群管理类 Cluster 将多个 Invoker 合并成一个实例。合并后的 Invoker 实例已经具备调用本地或远程服务的能力了，但并不能将此实例暴露给用户使用，这会对用户业务代码造成侵入。此时框架还需要通过代理工厂类 (ProxyFactory) 为服务接口生成代理类，并让代理类去调用 Invoker 逻辑。避免了 Dubbo 框架代码对业务代码的侵入，同时也让框架更容易使用。 



#### 解析ReferenceBean

由于在consume端，对需要应用的远程接口会添加@Autowired或者@Resource注解进行标记，在获取或者注入引用了远程接口的Bean时开始解析ReferenceBean

ReferenceBean因为实现了InitializingBean接口，同样会在实例（dubbo:reference所配置的接口）属性装配完成后调用afterPropertiesSet方法

```java

public class ReferenceBean<T> extends ReferenceConfig<T> implements FactoryBean, ApplicationContextAware, InitializingBean, DisposableBean {
    
    @Override
    @SuppressWarnings({"unchecked"})
    public void afterPropertiesSet() throws Exception {
        
        //各种的get
        Boolean b = isInit();
        if (b == null && getConsumer() != null) {
            b = getConsumer().isInit();
        }
        if (b != null && b.booleanValue()) {
            //调用getObject方法，开始实例注入
            getObject();
        }
    }
    
    @Override
    public Object getObject() throws Exception {
        //子类没有重写get，因此进入到ReferenceConfig的get方法中
        return get();
    }
    
}

```



#### 进入ReferenceConfig的get方法逻辑

```java

public class ReferenceConfig<T> extends AbstractReferenceConfig {
    
    public synchronized T get() {
        if (destroyed) {
            throw new IllegalStateException("Already destroyed!");
        }
        if (ref == null) {
            //当引用对象为空时（由于是远程引用，因此ref此时一定是null），进行初始化
            init();
        }
        return ref;
    }
    
    private void init() {
        
        //各种的检查、配置文件加载、get、put、append
        
        //attributes are stored by system context.
        StaticContext.getSystemContext().putAll(attributes);
        /**
        	为刚才是null的ref创建一个代理对象，传入的是一个map
        	该map包含了dubbo:application的属性配置和dubbo的一些其他属性（版本号、pid等）
        	以及dubbo:reference的所有属性
        	还有接口引用的所有方法
        **/
        ref = createProxy(map);
        ConsumerModel consumerModel = new ConsumerModel(getUniqueServiceName(), this, ref, interfaceClass.getMethods());
        ApplicationModel.initConsumerModel(getUniqueServiceName(), consumerModel);
        
    }
    
    
    @SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
    private T createProxy(Map<String, String> map) {
        
        //判断是否为jvm的本地引用
        
        if (urls.size() == 1) {
            //基于spi机制的refer调用，进入到RegistryProtocol的refer方法
            //调用完成后会拿到consumer的执行器
            invoker = refprotocol.refer(interfaceClass, urls.get(0));
        } else {
            List<Invoker<?>> invokers = new ArrayList<Invoker<?>>();
            URL registryURL = null;
            for (URL url : urls) {
                //基于spi机制的refer调用，进入到RegistryProtocol的refer方法
                invokers.add(refprotocol.refer(interfaceClass, url));
                if (Constants.REGISTRY_PROTOCOL.equals(url.getProtocol())) {
                   registryURL = url; // use last registry url
                }
            }
                if (registryURL != null) { // registry url is available
                    // use AvailableCluster only when register's cluster is available
                    URL u = registryURL.addParameter(Constants.CLUSTER_KEY, AvailableCluster.NAME);
                    invoker = cluster.join(new StaticDirectory(u, invokers));
                } else { // not a registry url
                    invoker = cluster.join(new StaticDirectory(invokers));
                }
            }
        
        //....
        
        //最终代理对象创建完成会放到代理对象工厂中，从中取出对应的代理对象并返回
        return (T) proxyFactory.getProxy(invoker);
        
    }
    
    
}
```



#### 进入RegistryProtocol的refer方法逻辑

```java

public class RegistryProtocol implements Protocol {
    
    //...
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
        url = url.setProtocol(url.getParameter(Constants.REGISTRY_KEY, Constants.DEFAULT_REGISTRY)).removeParameter(Constants.REGISTRY_KEY);
        
        /**
        	在执行真正的服务引用逻辑前先将zkClient开启了，因为远程引用需要
        	获取zkServer里的provider的服务节点
        	因此在这里先开启zkClient并与zkServer连接（只是连接但还未进行服务注册）
        	那么这里往后走的逻辑与上面服务暴露过程中开启zkClient的逻辑/调用链路是一样的
        **/
        Registry registry = registryFactory.getRegistry(url);
        if (RegistryService.class.equals(type)) {
            return proxyFactory.getInvoker((T) registry, type, url);
        }

        // group="a,b" or group="*"
        Map<String, String> qs = StringUtils.parseQueryString(url.getParameterAndDecoded(Constants.REFER_KEY));
        String group = qs.get(Constants.GROUP_KEY);
        if (group != null && group.length() > 0) {
            if ((Constants.COMMA_SPLIT_PATTERN.split(group)).length > 1
                    || "*".equals(group)) {
                //执行真正的引用逻辑
                return doRefer(getMergeableCluster(), registry, type, url);
            }
        }
        /**
        	执行真正的引用逻辑
        	registry：前面开启zkClient时的注册器实例
        	type：引用对象的类型（接口类型）
        	url：远程服务的地址（包含zkServer的地址以及注册服务对象的path）
        **/
        return doRefer(cluster, registry, type, url);
    }
    
    
    //服务引用的逻辑（从zkServer进行对被引用服务的订阅）
    private <T> Invoker<T> doRefer(Cluster cluster, Registry registry, Class<T> type, URL url) {
        RegistryDirectory<T> directory = new RegistryDirectory<T>(type, url);
        directory.setRegistry(registry);
        directory.setProtocol(protocol);
        // all attributes of REFER_KEY
        Map<String, String> parameters = new HashMap<String, String>(directory.getUrl().getParameters());
        URL subscribeUrl = new URL(Constants.CONSUMER_PROTOCOL, parameters.remove(Constants.REGISTER_IP_KEY), 0, type.getName(), parameters);
        if (!Constants.ANY_VALUE.equals(url.getServiceInterface())
                && url.getParameter(Constants.REGISTER_KEY, true)) {
            URL registeredConsumerUrl = getRegisteredConsumerUrl(subscribeUrl, url);
            
            /**
            	在这里进行服务注册，由于服务消费者同样需要zk维护，因此也要向zk进行注册
            	注册逻辑和服务暴露时的服务注册一样
            **/
            registry.register(registeredConsumerUrl);
            directory.setRegisteredConsumerUrl(registeredConsumerUrl);
        }
        //服务订阅
        directory.subscribe(subscribeUrl.addParameter(Constants.CATEGORY_KEY,
                Constants.PROVIDERS_CATEGORY
                        + "," + Constants.CONFIGURATORS_CATEGORY
                        + "," + Constants.ROUTERS_CATEGORY));

        Invoker invoker = cluster.join(directory);
        
        //维护和管理本地注册表，向提供者消费者的本地注册表添加注册信息
        ProviderConsumerRegTable.registerConsumer(invoker, url, subscribeUrl, directory);
        return invoker;
    }
    
}


```

调用父类的subscribe方法

```java

public abstract class FailbackRegistry extends AbstractRegistry {
    
    @Override
    public void subscribe(URL url, NotifyListener listener) {
        super.subscribe(url, listener);
        removeFailedSubscribed(url, listener);
        try {
            // Sending a subscription request to the server side
            //向zkServer发送服务订阅的请求
            doSubscribe(url, listener);
        } catch (Exception e) {
            //异常处理
        }
    }
}

```



#### 进入ZookeeperRegistry的doSubscribe逻辑

```java

public class ZookeeperRegistry extends FailbackRegistry {
    
    //....
    
    @Override
    protected void doSubscribe(final URL url, final NotifyListener listener) {
        try {
            if (Constants.ANY_VALUE.equals(url.getServiceInterface())) {
                String root = toRootPath();
                ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.get(url);
                if (listeners == null) {
                    zkListeners.putIfAbsent(url, new ConcurrentHashMap<NotifyListener, ChildListener>());
                    listeners = zkListeners.get(url);
                }
                ChildListener zkListener = listeners.get(listener);
                if (zkListener == null) {
                    listeners.putIfAbsent(listener, new ChildListener() {
                        @Override
                        public void childChanged(String parentPath, List<String> currentChilds) {
                            //遍历引用
                            for (String child : currentChilds) {
                                child = URL.decode(child);
                                if (!anyServices.contains(child)) {
                                    anyServices.add(child);
                                    subscribe(url.setPath(child).addParameters(Constants.INTERFACE_KEY, child,
                                            Constants.CHECK_KEY, String.valueOf(false)), listener);
                                }
                            }
                        }
                    });
                    zkListener = listeners.get(listener);
                }
                //创建所有服务引用的节点
                zkClient.create(root, false);
                List<String> services = zkClient.addChildListener(root, zkListener);
                if (services != null && !services.isEmpty()) {
                    for (String service : services) {
                        service = URL.decode(service);
                        anyServices.add(service);
                        subscribe(url.setPath(service).addParameters(Constants.INTERFACE_KEY, service,
                                Constants.CHECK_KEY, String.valueOf(false)), listener);
                    }
                }
            } else {
                List<URL> urls = new ArrayList<URL>();
                //遍历引用
                for (String path : toCategoriesPath(url)) {
                    ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.get(url);
                    if (listeners == null) {
                        zkListeners.putIfAbsent(url, new ConcurrentHashMap<NotifyListener, ChildListener>());
                        listeners = zkListeners.get(url);
                    }
                    ChildListener zkListener = listeners.get(listener);
                    if (zkListener == null) {
                        listeners.putIfAbsent(listener, new ChildListener() {
                            @Override
                            public void childChanged(String parentPath, List<String> currentChilds) {
                                ZookeeperRegistry.this.notify(url, listener, toUrlsWithEmpty(url, parentPath, currentChilds));
                            }
                        });
                        zkListener = listeners.get(listener);
                    }
                    zkClient.create(path, false);
                    List<String> children = zkClient.addChildListener(path, zkListener);
                    if (children != null) {
                        urls.addAll(toUrlsWithEmpty(url, path, children));
                    }
                }
                //唤醒监听器
                notify(url, listener, urls);
            }
        } catch (Throwable e) {
            throw new RpcException("Failed to subscribe " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }
        

```



#### 唤醒监听器后进入DubboProtocol的refer方法逻辑

在完成服务节点的创建后，会调用notify方法，该方法又会进入新的调用链，最终会去调用到DubboProtocol的refer方法（spi机制）

```java
public class DubboProtocol extends AbstractProtocol {
    
    //...
    
    @Override
    public <T> Invoker<T> refer(Class<T> serviceType, URL url) throws RpcException {
        optimizeSerialization(url);
        // create rpc invoker.
        //创建dubbo的负责远程服务调用的调用器，这也是dubbo的核心模型之一
        /**
        	创建该调用器时，会调用getClient方法来获取用于远程服务调用的客户端
        	该客户端是一个ExchangeClient对象，其底层是一个NettyClient对象
        **/
        DubboInvoker<T> invoker = new DubboInvoker<T>(serviceType, url, getClients(url), invokers);
        invokers.add(invoker);
        return invoker;
    }
    
}


```

后面方法调用链/调用逻辑就是通过Transporter去创建一个NettyClient实例，并且启动该netty客户端与远程的provider的netty服务端进行连接，最后将NettyClient封装成exchangeClient并返回，最后完成dubboInvoker的创建，如果多个invoker会将他们封装成一个cluster。





doSubscribe的方法逻辑走完后就会开始往上回调，回到顶层调用；到这里服务引用的流程就完了，服务引用一共做了以下几件事：

- 开启zkClient
- 注册服务，订阅服务
- 在zkServer上创建全部引用服务的节点
- 创建
- 创建代理对象（实质是一个DubboInvoker或cluster），将该代理对象回传给spring容器

官方的服务引用源码解读：https://dubbo.apache.org/zh-cn/docs/source_code_guide/refer-service.html



### dubbo服务调用流程

dubbo服务调用链

![](/dubbo-extension.jpg)

 首先服务消费者通过代理对象 Proxy 发起远程调用，接着通过网络客户端 Client 将编码后的请求发送给服务提供方的网络层上，也就是 Server。Server 在收到请求后，首先要做的事情是对数据包进行解码。然后将解码后的请求发送至分发器 Dispatcher，再由分发器将请求派发到指定的线程池上，最后由线程池调用具体的服务。这就是一个远程调用请求的发送与接收过程 。

大致流程就是：

1. 
2. 通过NettyClient与NettyServer进行远程通讯，将请求数据序列化并且编码发送给服务端
3. 等待服务端将请求的response返回，将结果解码，通过回调返回给顶层的方法调用



如果有本地存根，会先进入到本地存根的方法逻辑中，然后再发起远程调用。



#### 进入InvokerInvocationHandler的invoke方法逻辑

```java

	@Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(invoker, args);
        }
        /**如果是不需要远程调用的方法直接返回本地调用的结果**/
        if ("toString".equals(methodName) && parameterTypes.length == 0) {
            return invoker.toString();
        }
        if ("hashCode".equals(methodName) && parameterTypes.length == 0) {
            return invoker.hashCode();
        }
        if ("equals".equals(methodName) && parameterTypes.length == 1) {
            return invoker.equals(args[0]);
        }
        //进入MockClusterInvoker的invoke方法逻辑，做本地伪装处理
        return invoker.invoke(new RpcInvocation(method, args)).recreate();
    }

```

后面的调用链主要是对local、mock、cache的配置支持，这对应了图中第一层的filter，因此会进入相关调用进行判断是否做对应的处理



#### 进入MockClusterInvoker的invoke方法逻辑(本地伪装处理)

```java

@Override
    public Result invoke(Invocation invocation) throws RpcException {
        Result result = null;

        String value = directory.getUrl().getMethodParameter(invocation.getMethodName(), Constants.MOCK_KEY, Boolean.FALSE.toString()).trim();
        if (value.length() == 0 || value.equalsIgnoreCase("false")) {
            //no mock
            //没有配置mock属性时（mock=false）进入到一下层的invoke调用
            result = this.invoker.invoke(invocation);
        } else if (value.startsWith("force")) {
            if (logger.isWarnEnabled()) {
                logger.info("force-mock: " + invocation.getMethodName() + " force-mock enabled , url : " + directory.getUrl());
            }
            //force:direct mock
            result = doMockInvoke(invocation, null);
        } else {
            //fail-mock
            try {
                result = this.invoker.invoke(invocation);
            } catch (RpcException e) {
                if (e.isBiz()) {
                    throw e;
                } else {
                    if (logger.isWarnEnabled()) {
                        logger.warn("fail-mock: " + invocation.getMethodName() + " fail-mock enabled , url : " + directory.getUrl(), e);
                    }
                    result = doMockInvoke(invocation, e);
                }
            }
        }
        return result;
    }

```



#### 进入到AbstractClusterInvoker的invoke方法逻辑

在该层会初始化负载均衡策略（默认是random），并且会在下一层根据该策略做invoker的选择

```java

	@Override
    public Result invoke(final Invocation invocation) throws RpcException {
        checkWhetherDestroyed();
        LoadBalance loadbalance = null;

        // binding attachments into invocation.
        Map<String, String> contextAttachments = RpcContext.getContext().getAttachments();
        if (contextAttachments != null && contextAttachments.size() != 0) {
            ((RpcInvocation) invocation).addAttachments(contextAttachments);
        }

        List<Invoker<T>> invokers = list(invocation);
        if (invokers != null && !invokers.isEmpty()) {
            loadbalance = ExtensionLoader.getExtensionLoader(LoadBalance.class).getExtension(invokers.get(0).getUrl()
                    .getMethodParameter(RpcUtils.getMethodName(invocation), Constants.LOADBALANCE_KEY, Constants.DEFAULT_LOADBALANCE));
        }
        RpcUtils.attachInvocationIdIfAsync(getUrl(), invocation);
        /**
        	调用真正的invoke逻辑，具体调用的子类会根据集群容错策略的配置而进行选择
        	默认是failover机制，因此会进入到FailOverClusterInvoker的doInvoke方法
        **/
        return doInvoke(invocation, invokers, loadbalance);
    }

```



#### 根据集群容错策略进入doInvoke方法逻辑

默认的集群容错策略是failover，因此默认进入的是FailOverClusterInvoker中

```java
	
	@Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Result doInvoke(Invocation invocation, final List<Invoker<T>> invokers, LoadBalance loadbalance) throws RpcException {
        List<Invoker<T>> copyinvokers = invokers;
        checkInvokers(copyinvokers, invocation);
        int len = getUrl().getMethodParameter(invocation.getMethodName(), Constants.RETRIES_KEY, Constants.DEFAULT_RETRIES) + 1;
        if (len <= 0) {
            len = 1;
        }
        // retry loop.
        RpcException le = null; // last exception.
        List<Invoker<T>> invoked = new ArrayList<Invoker<T>>(copyinvokers.size()); // invoked invokers.
        Set<String> providers = new HashSet<String>(len);
        for (int i = 0; i < len; i++) {
            //Reselect before retry to avoid a change of candidate `invokers`.
            //NOTE: if `invokers` changed, then `invoked` also lose accuracy.
            if (i > 0) {
                checkWhetherDestroyed();
                copyinvokers = list(invocation);
                // check again
                checkInvokers(copyinvokers, invocation);
            }
            
            /**
            	根据负载均衡策略，在cluster中选择出最终进行远程服务调用的invoker
            **/
            Invoker<T> invoker = select(loadbalance, invocation, copyinvokers, invoked);
            invoked.add(invoker);
            RpcContext.getContext().setInvokers((List) invoked);
            try {
                /**
                	再次发起invoke，此时已经选择的具体进行远程调用的invoker
                	而根据dubbo的设计原则，会先进入到AbstractXXX中走一遍方法逻辑
                	再在该抽象类中再次调用抽象方法，再进入到具体的实现类方法
                	最终肯定会走到DubboInvoker中
                **/
                Result result = invoker.invoke(invocation);
                if (le != null && logger.isWarnEnabled()) {
                    logger.warn("Although retry the method " + invocation.getMethodName()
                            + " in the service " + getInterface().getName()
                            + " was successful by the provider " + invoker.getUrl().getAddress()
                            + ", but there have been failed providers " + providers
                            + " (" + providers.size() + "/" + copyinvokers.size()
                            + ") from the registry " + directory.getUrl().getAddress()
                            + " on the consumer " + NetUtils.getLocalHost()
                            + " using the dubbo version " + Version.getVersion() + ". Last error is: "
                            + le.getMessage(), le);
                }
                return result;
            } catch (RpcException e) {
                //异常处理
            }
    }

```



#### 进入到AbstractInvoker的invoke方法逻辑

```java
@Override
public Result invoke(Invocation inv) throws RpcException {
    //...
    
    try {
        	//进入最终的DubboInvoker的doInvoke方法
            return doInvoke(invocation);
        } catch (InvocationTargetException e) { // biz exception
            //异常处理
        } catch (RpcException e) {
             //异常处理
        } catch (Throwable e) {
            //异常处理
        }
}
```



#### 进入到DubboInvoker的doInvoke方法逻辑

```java

//初始化DubboInvoker时，就已经将client数组传入，这是在服务引入流程做的
//因此在doInvoke方法中可以直接拿到该客户端
public DubboInvoker(Class<T> serviceType, URL url, ExchangeClient[] clients) {
        this(serviceType, url, clients, null);
    }


	@Override
    protected Result doInvoke(final Invocation invocation) throws Throwable {
        RpcInvocation inv = (RpcInvocation) invocation;
        final String methodName = RpcUtils.getMethodName(invocation);
        inv.setAttachment(Constants.PATH_KEY, getUrl().getPath());
        inv.setAttachment(Constants.VERSION_KEY, version);

        //需要通过exchangeClient进行远程调用，因此需要初始化该客户端
        ExchangeClient currentClient;
        if (clients.length == 1) {
            //服务引入时以及将客户端数组传入DubboInvoker对象中，因此直接这里拿到该客户端实例
            currentClient = clients[0];
        } else {
            //轮询获取客户端
            currentClient = clients[index.getAndIncrement() % clients.length];
        }
        try {
            boolean isAsync = RpcUtils.isAsync(getUrl(), invocation);
            boolean isOneway = RpcUtils.isOneway(getUrl(), invocation);
            int timeout = getUrl().getMethodParameter(methodName, Constants.TIMEOUT_KEY, Constants.DEFAULT_TIMEOUT);
            if (isOneway) {
                boolean isSent = getUrl().getMethodParameter(methodName, Constants.SENT_KEY, false);
                //如果是单向的请求，在这里进行数据发送
                currentClient.send(inv, isSent);
                //单向请求不需要等待服务端返回结果，因此直接将resp设置为null
                RpcContext.getContext().setFuture(null);
                return new RpcResult();
            } else if (isAsync) {
                ResponseFuture future = currentClient.request(inv, timeout);
                RpcContext.getContext().setFuture(new FutureAdapter<Object>(future));
                return new RpcResult();
            } else {
                RpcContext.getContext().setFuture(null);
                //通过客户端向服务端发起远程通讯，发送本次请求
                //后面的逻辑调用会一层一层地最终进入到NettyChannel中
                return (Result) currentClient.request(inv, timeout).get();
            }
        } catch (TimeoutException e) {
            //异常处理
        } catch (RemotingException e) {
            //异常处理
        }
    }

```



#### 进入HeaderExchangeChannel的request逻辑

HeaderExchangeClient实现了ExchangeClient接口，因此先进入该HeaderExchangeClient.request，此时会通过channel调用request方法（return channel.request），因此会进入到HeaderExchangeChannel中

```java

	@Override
	//请求发送的具体逻辑
    public ResponseFuture request(Object request, int timeout) throws RemotingException {
        if (closed) {
            throw new RemotingException(this.getLocalAddress(), null, "Failed to send request " + request + ", cause: The channel " + this + " is closed!");
        }
        // create request. 封装请求数据以及配置属性
        Request req = new Request();
        req.setVersion(Version.getProtocolVersion());
        req.setTwoWay(true);
        req.setData(request);
        DefaultFuture future = new DefaultFuture(channel, req, timeout);
        try {
            //通过channel发送请求
            channel.send(req);
        } catch (RemotingException e) {
            future.cancel();
            throw e;
        }
        return future;
    }

```



#### 进入NettyChannel中进行真正的请求发送

在HeaderExchangeChannel进行channel.send调用后，会经过AbstractPeer的send方法，在经过AbstractClient的send方法，在AbstractClient中进行channel获取，当获取到真正和服务端进行连接的channel对象后，即NettyChannel对象，才可以最终调用NettyChannel的send方法，向服务端发送数据

```java

	//AbstractClient
	@Override
    public void send(Object message, boolean sent) throws RemotingException {
        if (send_reconnect && !isConnected()) {
            connect();
        }
        Channel channel = getChannel();
        //TODO Can the value returned by getChannel() be null? need improvement.
        if (channel == null || !channel.isConnected()) {
            throw new RemotingException(this, "message can not send, because channel is closed . url:" + getUrl());
        }
        channel.send(message, sent);
    }

```

```java

	//NettyChannel
	@Override
    public void send(Object message, boolean sent) throws RemotingException {
        super.send(message, sent);
        boolean success = true;
        int timeout = 0;
        try {
            //向管道中数据写入并刷新，即向服务端进行数据发送，并且将服务端返回的结果封装到future对象中
            ChannelFuture future = channel.writeAndFlush(message);
            if (sent) {
                //获取服务超时配置
                timeout = getUrl().getPositiveParameter(Constants.TIMEOUT_KEY, Constants.DEFAULT_TIMEOUT);
                /**
                	等待服务端返回请求处理的结果并封装
                	如果服务端在超时时间内返回了结果那么本次调用成功
                	如果超过了该超时时间，如果没有配置重试会立刻抛出异常
                	如果配置retries，那么会在进行配置的重试次数的请求
                	如果重试都不成功就抛出超时异常
                **/
                success = future.await(timeout);
            }
            Throwable cause = future.cause();
            if (cause != null) {
                throw cause;
            }
        } catch (Throwable e) {
            throw new RemotingException(this, "Failed to send message " + message + " to " + getRemoteAddress() + ", cause: " + e.getMessage(), e);
        }

        if (!success) {
            throw new RemotingException(this, "Failed to send message " + message + " to " + getRemoteAddress()
                    + "in timeout(" + timeout + "ms) limit");
        }
    }

```

至此dubbo服务调用的流程就已经走完了

官方的服务调用源码解读：https://dubbo.apache.org/zh-cn/docs/source_code_guide/service-invoking-process.html















