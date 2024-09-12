/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.openfeign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import feign.Client;
import feign.Contract;
import feign.ExceptionPropagationPolicy;
import feign.Feign;
import feign.Logger;
import feign.QueryMapEncoder;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.Target.HardCodedTarget;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.openfeign.clientconfig.FeignClientConfigurer;
import org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient;
import org.springframework.cloud.openfeign.loadbalancer.RetryableFeignBlockingLoadBalancerClient;
import org.springframework.cloud.openfeign.ribbon.LoadBalancerFeignClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Spencer Gibb
 * @author Venil Noronha
 * @author Eko Kurniawan Khannedy
 * @author Gregor Zurowski
 * @author Matt King
 * @author Olga Maciaszek-Sharma
 * @author Ilia Ilinykh
 * @author Marcin Grzejszczak
 * @author Sam Kruglov
 */
public class FeignClientFactoryBean implements FactoryBean<Object>, InitializingBean,
	ApplicationContextAware, BeanFactoryAware {

	/***********************************
	 * WARNING! Nothing in this class should be @Autowired. It causes NPEs because of some
	 * lifecycle race condition.
	 ***********************************/

	private static Log LOG = LogFactory.getLog(FeignClientFactoryBean.class);

	private Class<?> type;

	private String name;

	private String url;

	private String contextId;

	private String path;

	private boolean decode404;

	private boolean inheritParentContext = true;

	private ApplicationContext applicationContext;

	private BeanFactory beanFactory;

	private Class<?> fallback = void.class;

	private Class<?> fallbackFactory = void.class;

	private int readTimeoutMillis = new Request.Options().readTimeoutMillis();

	private int connectTimeoutMillis = new Request.Options().connectTimeoutMillis();

	private boolean followRedirects = new Request.Options().isFollowRedirects();

	private List<FeignBuilderCustomizer> additionalCustomizers = new ArrayList<>();

	@Override
	public void afterPropertiesSet() {
		Assert.hasText(contextId, "Context id must be set");
		Assert.hasText(name, "Name must be set");
	}

	protected Feign.Builder feign(FeignContext context) {
		// 传入类型 从Spring容器中拿到对应的context 并设置到builder中
		FeignLoggerFactory loggerFactory = get(context, FeignLoggerFactory.class);
		Logger logger = loggerFactory.create(type);
		// 拿到 context里面的组件 诸如：Encoder、Decoder、Contract等等
		// @formatter:off
		Feign.Builder builder = get(context, Feign.Builder.class)//TODO 进入该方法
				// required values
				.logger(logger)
				.encoder(get(context, Encoder.class))
				.decoder(get(context, Decoder.class))
				.contract(get(context, Contract.class));
		// @formatter:on

		configureFeign(context, builder);
		applyBuildCustomizers(context, builder);

		return builder;
	}

	private void applyBuildCustomizers(FeignContext context, Feign.Builder builder) {
		Map<String, FeignBuilderCustomizer> customizerMap = context
			.getInstances(contextId, FeignBuilderCustomizer.class);

		if (customizerMap != null) {
			customizerMap.values().stream()
				.sorted(AnnotationAwareOrderComparator.INSTANCE)
				.forEach(feignBuilderCustomizer -> feignBuilderCustomizer
					.customize(builder));
		}
		additionalCustomizers.forEach(customizer -> customizer.customize(builder));
	}

	protected void configureFeign(FeignContext context, Feign.Builder builder) {
		FeignClientProperties properties = beanFactory != null
			? beanFactory.getBean(FeignClientProperties.class)
			: applicationContext.getBean(FeignClientProperties.class);

		FeignClientConfigurer feignClientConfigurer = getOptional(context,
			FeignClientConfigurer.class);
		setInheritParentContext(feignClientConfigurer.inheritParentConfiguration());

		if (properties != null && inheritParentContext) {
			if (properties.isDefaultToProperties()) {
				configureUsingConfiguration(context, builder);
				configureUsingProperties(
					properties.getConfig().get(properties.getDefaultConfig()),
					builder);
				configureUsingProperties(properties.getConfig().get(contextId), builder);
			} else {
				configureUsingProperties(
					properties.getConfig().get(properties.getDefaultConfig()),
					builder);
				configureUsingProperties(properties.getConfig().get(contextId), builder);
				configureUsingConfiguration(context, builder);
			}
		} else {
			configureUsingConfiguration(context, builder);
		}
	}

	protected void configureUsingConfiguration(FeignContext context,
											   Feign.Builder builder) {
		Logger.Level level = getInheritedAwareOptional(context, Logger.Level.class);
		if (level != null) {
			builder.logLevel(level);
		}
		Retryer retryer = getInheritedAwareOptional(context, Retryer.class);
		if (retryer != null) {
			builder.retryer(retryer);
		}
		ErrorDecoder errorDecoder = getInheritedAwareOptional(context,
			ErrorDecoder.class);
		if (errorDecoder != null) {
			builder.errorDecoder(errorDecoder);
		} else {
			FeignErrorDecoderFactory errorDecoderFactory = getOptional(context,
				FeignErrorDecoderFactory.class);
			if (errorDecoderFactory != null) {
				ErrorDecoder factoryErrorDecoder = errorDecoderFactory.create(type);
				builder.errorDecoder(factoryErrorDecoder);
			}
		}
		Request.Options options = getInheritedAwareOptional(context,
			Request.Options.class);
		if (options != null) {
			builder.options(options);
			readTimeoutMillis = options.readTimeoutMillis();
			connectTimeoutMillis = options.connectTimeoutMillis();
			followRedirects = options.isFollowRedirects();
		}
		Map<String, RequestInterceptor> requestInterceptors = getInheritedAwareInstances(
			context, RequestInterceptor.class);
		if (requestInterceptors != null) {
			List<RequestInterceptor> interceptors = new ArrayList<>(
				requestInterceptors.values());
			AnnotationAwareOrderComparator.sort(interceptors);
			builder.requestInterceptors(interceptors);
		}
		QueryMapEncoder queryMapEncoder = getInheritedAwareOptional(context,
			QueryMapEncoder.class);
		if (queryMapEncoder != null) {
			builder.queryMapEncoder(queryMapEncoder);
		}
		if (decode404) {
			builder.decode404();
		}
		ExceptionPropagationPolicy exceptionPropagationPolicy = getInheritedAwareOptional(
			context, ExceptionPropagationPolicy.class);
		if (exceptionPropagationPolicy != null) {
			builder.exceptionPropagationPolicy(exceptionPropagationPolicy);
		}
	}

	protected void configureUsingProperties(
		FeignClientProperties.FeignClientConfiguration config,
		Feign.Builder builder) {
		if (config == null) {
			return;
		}

		if (config.getLoggerLevel() != null) {
			builder.logLevel(config.getLoggerLevel());
		}

		connectTimeoutMillis = config.getConnectTimeout() != null
			? config.getConnectTimeout() : connectTimeoutMillis;
		readTimeoutMillis = config.getReadTimeout() != null ? config.getReadTimeout()
			: readTimeoutMillis;
		followRedirects = config.isFollowRedirects() != null ? config.isFollowRedirects()
			: followRedirects;

		builder.options(new Request.Options(connectTimeoutMillis, TimeUnit.MILLISECONDS,
			readTimeoutMillis, TimeUnit.MILLISECONDS, followRedirects));

		if (config.getRetryer() != null) {
			Retryer retryer = getOrInstantiate(config.getRetryer());
			builder.retryer(retryer);
		}

		if (config.getErrorDecoder() != null) {
			ErrorDecoder errorDecoder = getOrInstantiate(config.getErrorDecoder());
			builder.errorDecoder(errorDecoder);
		}

		if (config.getRequestInterceptors() != null
			&& !config.getRequestInterceptors().isEmpty()) {
			// this will add request interceptor to builder, not replace existing
			for (Class<RequestInterceptor> bean : config.getRequestInterceptors()) {
				RequestInterceptor interceptor = getOrInstantiate(bean);
				builder.requestInterceptor(interceptor);
			}
		}

		if (config.getDecode404() != null) {
			if (config.getDecode404()) {
				builder.decode404();
			}
		}

		if (Objects.nonNull(config.getEncoder())) {
			builder.encoder(getOrInstantiate(config.getEncoder()));
		}

		if (Objects.nonNull(config.getDefaultRequestHeaders())) {
			builder.requestInterceptor(requestTemplate -> requestTemplate
				.headers(config.getDefaultRequestHeaders()));
		}

		if (Objects.nonNull(config.getDefaultQueryParameters())) {
			builder.requestInterceptor(requestTemplate -> requestTemplate
				.queries(config.getDefaultQueryParameters()));
		}

		if (Objects.nonNull(config.getDecoder())) {
			builder.decoder(getOrInstantiate(config.getDecoder()));
		}

		if (Objects.nonNull(config.getContract())) {
			builder.contract(getOrInstantiate(config.getContract()));
		}

		if (Objects.nonNull(config.getExceptionPropagationPolicy())) {
			builder.exceptionPropagationPolicy(config.getExceptionPropagationPolicy());
		}
	}

	private <T> T getOrInstantiate(Class<T> tClass) {
		try {
			return beanFactory != null ? beanFactory.getBean(tClass)
				: applicationContext.getBean(tClass);
		} catch (NoSuchBeanDefinitionException e) {
			return BeanUtils.instantiateClass(tClass);
		}
	}

	/**
	 * TODO 来自org.springframework.cloud.openfeign.FeignClientFactoryBean#feign(org.springframework.cloud.openfeign.FeignContext)
	 *
	 * @param context
	 * @param type
	 * @param <T>
	 * @return
	 */
	protected <T> T get(FeignContext context, Class<T> type) {
		/**
		 * TODO 进入该方法
		 * 本质是org.springframework.cloud.context.named.NamedContextFactory.getInstance(java.lang.String, java.lang.Class<T>)
		 * 在NamedContextFactory#getContext()方法中，会先去成员属性 Map<String, AnnotationConfigApplicationContext> contexts中那上下文对象，
		 * 如果是第一次当然没有，那就调用NamedContextFactory#createContext()方法创建之，在这个方法中会创建AnnotationConfigApplicationContext对象
		 * 创建完之后 给这个
		 * AnnotationConfigApplicationContext对象 设置parent属性
		 * 这个parent 是因为 NamedContextFactory实现了ApplicationContextAware接口
		 * 把Spring Boot的构建的上下文赋值给了parent
		 *
		 * 这个parent属性就包括了Spring Boot中所有的对象，当然也就包括扫描自动装配的类了，当然也就包括在配置类中声明的feign组件了
		 */
		T instance = context.getInstance(contextId, type);
		if (instance == null) {
			throw new IllegalStateException(
				"No bean found of type " + type + " for " + contextId);
		}
		return instance;
	}

	protected <T> T getOptional(FeignContext context, Class<T> type) {
		return context.getInstance(contextId, type);
	}

	protected <T> T getInheritedAwareOptional(FeignContext context, Class<T> type) {
		if (inheritParentContext) {
			return getOptional(context, type);
		} else {
			return context.getInstanceWithoutAncestors(contextId, type);
		}
	}

	protected <T> Map<String, T> getInheritedAwareInstances(FeignContext context,
															Class<T> type) {
		if (inheritParentContext) {
			return context.getInstances(contextId, type);
		} else {
			return context.getInstancesWithoutAncestors(contextId, type);
		}
	}

	protected <T> T loadBalance(Feign.Builder builder, FeignContext context,
								HardCodedTarget<T> target) {
		Client client = getOptional(context, Client.class);
		if (client != null) {
			builder.client(client);
			Targeter targeter = get(context, Targeter.class);
			return targeter.target(this, builder, context, target);
		}

		throw new IllegalStateException(
			"No Feign Client for loadBalancing defined. Did you forget to include spring-cloud-starter-netflix-ribbon or spring-cloud-starter-loadbalancer?");
	}

	/**
	 * TODO 重点
	 *
	 * @return
	 */
	@Override
	public Object getObject() {
		//TODO 进入
		return getTarget();
	}

	/**
	 * @param <T> the target type of the Feign client
	 * @return a {@link Feign} client created with the specified data and the context
	 * information
	 */
	<T> T getTarget() {
		// 容Spring 容器中 得到FeignContext对象
		//TODO FeignAutoConfiguration里面创建的 FeignContext
		FeignContext context = beanFactory != null
			? beanFactory.getBean(FeignContext.class)
			: applicationContext.getBean(FeignContext.class);
		//TODO 进入该方法
		Feign.Builder builder = feign(context);

		if (!StringUtils.hasText(url)) {

			if (LOG.isInfoEnabled()) {
				LOG.info("For '" + name
					+ "' URL not provided. Will try picking an instance via load-balancing.");
			}
			if (!name.startsWith("http")) {
				url = "http://" + name;
			} else {
				url = name;
			}
			url += cleanPath();
			return (T) loadBalance(builder, context,
				new HardCodedTarget<>(type, name, url));
		}
		if (StringUtils.hasText(url) && !url.startsWith("http")) {
			url = "http://" + url;
		}
		String url = this.url + cleanPath();
		// 最终得到的事loadBalancerFeignClient对象 通过它发起http远程调用
		/**
		 * FeignAutoConfiguration中配置了两个Client的实现类，即ApacheHttpClient和OkHttpClient
		 *
		 * ApacheHttpClient为例，它是声明在FeignAutoConfiguration.HttpClientFeignConfiguration配置类中的
		 *
		 * 但是该类上有一个注解，即@ConditionalOnMissingClass(“com.netflix.loadbalancer.ILoadBalancer”)，
		 * 表示只有当缺失了 com.netflix.loadbalancer.ILoadBalancer接口(即没有对应的依赖)
		 * 这个ILoadBalancer和ribbon有关
		 *
		 * 我们在微服务开发的时候，一般是feign和ribbon配合使用的，这两个框架的依赖都会引入，
		 * 所以，FeignAutoConfiguration.HttpClientFeignConfiguration不会被Spring扫描到Spring容器中
		 * ，因为不满足条件注解的条件，
		 * 所以ApacheHttpClient和OkHttpClient这两个Client接口的实现类也就不会被实例化到Spring
		 *
		 * 查看OkHttpFeignLoadBalancedConfiguration和HttpClientFeignLoadBalancedConfiguration
		 */
		Client client = getOptional(context, Client.class);
		if (client != null) {
			if (client instanceof LoadBalancerFeignClient) {
				// not load balancing because we have a url,
				// but ribbon is on the classpath, so unwrap
				client = ((LoadBalancerFeignClient) client).getDelegate();
			}
			if (client instanceof FeignBlockingLoadBalancerClient) {
				// not load balancing because we have a url,
				// but Spring Cloud LoadBalancer is on the classpath, so unwrap
				client = ((FeignBlockingLoadBalancerClient) client).getDelegate();
			}
			if (client instanceof RetryableFeignBlockingLoadBalancerClient) {
				// not load balancing because we have a url,
				// but Spring Cloud LoadBalancer is on the classpath, so unwrap
				client = ((RetryableFeignBlockingLoadBalancerClient) client)
					.getDelegate();
			}
			// 将client设置到builder属性中
			builder.client(client);
		}
		//TODO Targeter是 org.springframework.cloud.openfeign.FeignAutoConfiguration 里面创建的
		Targeter targeter = get(context, Targeter.class);
		// 通过动态代理生辰给的this.type对象并返回
		// 查看 org.springframework.cloud.openfeign.DefaultTargeter.target
		return (T) targeter.target(this, builder, context,
			new HardCodedTarget<>(type, name, url));
	}

	private String cleanPath() {
		String path = this.path.trim();
		if (StringUtils.hasLength(path)) {
			if (!path.startsWith("/")) {
				path = "/" + path;
			}
			if (path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}
		}
		return path;
	}

	@Override
	public Class<?> getObjectType() {
		return type;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

	public Class<?> getType() {
		return type;
	}

	public void setType(Class<?> type) {
		this.type = type;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getContextId() {
		return contextId;
	}

	public void setContextId(String contextId) {
		this.contextId = contextId;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public boolean isDecode404() {
		return decode404;
	}

	public void setDecode404(boolean decode404) {
		this.decode404 = decode404;
	}

	public boolean isInheritParentContext() {
		return inheritParentContext;
	}

	public void setInheritParentContext(boolean inheritParentContext) {
		this.inheritParentContext = inheritParentContext;
	}

	public void addCustomizer(FeignBuilderCustomizer customizer) {
		additionalCustomizers.add(customizer);
	}

	public ApplicationContext getApplicationContext() {
		return applicationContext;
	}

	@Override
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		applicationContext = context;
		beanFactory = context;
	}

	public Class<?> getFallback() {
		return fallback;
	}

	public void setFallback(Class<?> fallback) {
		this.fallback = fallback;
	}

	public Class<?> getFallbackFactory() {
		return fallbackFactory;
	}

	public void setFallbackFactory(Class<?> fallbackFactory) {
		this.fallbackFactory = fallbackFactory;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		FeignClientFactoryBean that = (FeignClientFactoryBean) o;
		return Objects.equals(applicationContext, that.applicationContext)
			&& Objects.equals(beanFactory, that.beanFactory)
			&& decode404 == that.decode404
			&& inheritParentContext == that.inheritParentContext
			&& Objects.equals(fallback, that.fallback)
			&& Objects.equals(fallbackFactory, that.fallbackFactory)
			&& Objects.equals(name, that.name) && Objects.equals(path, that.path)
			&& Objects.equals(type, that.type) && Objects.equals(url, that.url)
			&& Objects.equals(connectTimeoutMillis, that.connectTimeoutMillis)
			&& Objects.equals(readTimeoutMillis, that.readTimeoutMillis)
			&& Objects.equals(followRedirects, that.followRedirects);
	}

	@Override
	public int hashCode() {
		return Objects.hash(applicationContext, beanFactory, decode404,
			inheritParentContext, fallback, fallbackFactory, name, path, type, url,
			readTimeoutMillis, connectTimeoutMillis, followRedirects);
	}

	@Override
	public String toString() {
		return new StringBuilder("FeignClientFactoryBean{").append("type=").append(type)
			.append(", ").append("name='").append(name).append("', ").append("url='")
			.append(url).append("', ").append("path='").append(path).append("', ")
			.append("decode404=").append(decode404).append(", ")
			.append("inheritParentContext=").append(inheritParentContext).append(", ")
			.append("applicationContext=").append(applicationContext).append(", ")
			.append("beanFactory=").append(beanFactory).append(", ")
			.append("fallback=").append(fallback).append(", ")
			.append("fallbackFactory=").append(fallbackFactory).append("}")
			.append("connectTimeoutMillis=").append(connectTimeoutMillis).append("}")
			.append("readTimeoutMillis=").append(readTimeoutMillis).append("}")
			.append("followRedirects=").append(followRedirects).append("}")
			.toString();
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

}
