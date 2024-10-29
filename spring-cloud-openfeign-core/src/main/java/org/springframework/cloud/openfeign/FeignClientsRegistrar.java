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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * @author Spencer Gibb
 * @author Jakub Narloch
 * @author Venil Noronha
 * @author Gang Li
 * @author Michal Domagala
 * @author Marcin Grzejszczak
 * @author Olga Maciaszek-Sharma
 */
class FeignClientsRegistrar
	implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, EnvironmentAware {

	// patterned after Spring Integration IntegrationComponentScanRegistrar
	// and RibbonClientsConfigurationRegistgrar

	private ResourceLoader resourceLoader;

	private Environment environment;

	FeignClientsRegistrar() {
	}

	static void validateFallback(final Class clazz) {
		Assert.isTrue(!clazz.isInterface(),
			"Fallback class must implement the interface annotated by @FeignClient");
	}

	static void validateFallbackFactory(final Class clazz) {
		Assert.isTrue(!clazz.isInterface(), "Fallback factory must produce instances "
			+ "of fallback classes that implement the interface annotated by @FeignClient");
	}

	static String getName(String name) {
		if (!StringUtils.hasText(name)) {
			return "";
		}

		String host = null;
		try {
			String url;
			if (!name.startsWith("http://") && !name.startsWith("https://")) {
				url = "http://" + name;
			} else {
				url = name;
			}
			host = new URI(url).getHost();

		} catch (URISyntaxException e) {
		}
		Assert.state(host != null, "Service id not legal hostname (" + name + ")");
		return name;
	}

	static String getUrl(String url) {
		// 检查 `url` 是否为非空且包含内容，并且不是表达式格式（例如 `#{...}`）
		if (StringUtils.hasText(url) && !(url.startsWith("#{") && url.contains("}"))) {
			if (!url.contains("://")) { // 如果 `url` 不包含协议（例如 `://`），默认加上 "http://"
				url = "http://" + url;
			}
			try {    // 尝试将 `url` 转换为 `URL` 对象，以验证其格式是否正确
				new URL(url);
			} catch (MalformedURLException e) {// 如果 `url` 格式错误，抛出 `IllegalArgumentException` 异常，提示格式不合法
				throw new IllegalArgumentException(url + " is malformed", e);
			}
		}
		return url;
	}

	static String getPath(String path) {
		if (StringUtils.hasText(path)) {  // 检查 `path` 是否非空并去掉首尾的空格
			path = path.trim();
			if (!path.startsWith("/")) {  // 如果 `path` 不以 `/` 开头，给它加上前导的 `/`
				path = "/" + path;
			}
			if (path.endsWith("/")) { // 如果 `path` 以 `/` 结尾，去掉尾部的 `/`
				path = path.substring(0, path.length() - 1);
			}
		}
		return path;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	@Override
	public void registerBeanDefinitions(AnnotationMetadata metadata,
										BeanDefinitionRegistry registry) {

		// 注册默认配置 TODO 进入
		registerDefaultConfiguration(metadata, registry);

		// 注册 FeignClient TODO 进入
		registerFeignClients(metadata, registry);
	}


	String getName(ConfigurableBeanFactory beanFactory, Map<String, Object> attributes) {
		String name = (String) attributes.get("serviceId");// 首先尝试从 `attributes` 中获取 `serviceId` 属性的值
		if (!StringUtils.hasText(name)) { // 如果 `serviceId` 为空或仅包含空格，则尝试获取 `name` 属性的值
			name = (String) attributes.get("name");
		}
		if (!StringUtils.hasText(name)) { // 如果 `name` 也为空或仅包含空格，则尝试获取 `value` 属性的值
			name = (String) attributes.get("value");
		}
		// 调用 `resolve` 方法，将 `name` 中的占位符和表达式进行解析
		name = resolve(beanFactory, name);
		return getName(name); // 调用 `getName(String)` 方法，进一步处理和返回最终的名称
	}

	private String getContextId(ConfigurableBeanFactory beanFactory,
								Map<String, Object> attributes) {
		String contextId = (String) attributes.get("contextId");
		if (!StringUtils.hasText(contextId)) {
			return getName(attributes);
		}

		contextId = resolve(beanFactory, contextId);
		return getName(contextId);
	}

	/**
	 *  定义一个私有方法 `registerDefaultConfiguration`，用于注册默认的 Feign 配置
	 *  参数 `metadata` 包含 `@EnableFeignClients` 注解所在类的元数据
	 *  参数 `registry` 是 Spring 的 `BeanDefinitionRegistry`，用于注册 Bean 定义
	 * @param metadata
	 * @param registry
	 */
	private void registerDefaultConfiguration(AnnotationMetadata metadata,
											  BeanDefinitionRegistry registry) {
		// 从 `metadata` 中获取 `@EnableFeignClients` 注解的属性值映射 `defaultAttrs`
		// `true` 表示要检索注解的所有属性（包括继承的属性）
		Map<String, Object> defaultAttrs = metadata
			.getAnnotationAttributes(EnableFeignClients.class.getName(), true);
		// 检查 `defaultAttrs` 是否不为 null 并且包含 `defaultConfiguration` 属性
		// `defaultConfiguration` 属性用于指定 Feign 客户端的默认配置类
		if (defaultAttrs != null && defaultAttrs.containsKey("defaultConfiguration")) {
			String name;
			if (metadata.hasEnclosingClass()) { // 如果当前类是内部类，则将 `name` 设置为 "default." + 外部类的类名
				name = "default." + metadata.getEnclosingClassName();
			} else { // 否则，将 `name` 设置为 "default." + 当前类的类名
				name = "default." + metadata.getClassName();
			}
			// 调用 `registerClientConfiguration` 方法，将默认配置注册到 `registry` 中
			// 参数 `name` 用于生成 Bean 的名称，`defaultConfiguration` 指定了配置类
			registerClientConfiguration(registry, name,
				defaultAttrs.get("defaultConfiguration"));
		}
	}

	public void registerFeignClients(AnnotationMetadata metadata,
									 BeanDefinitionRegistry registry) {

		LinkedHashSet<BeanDefinition> candidateComponents = new LinkedHashSet<>();
		// 解析 @EnableFeginClient注解
		Map<String, Object> attrs = metadata
			.getAnnotationAttributes(EnableFeignClients.class.getName());
		// 获取 `clients` 属性值，如果存在，则直接使用；否则，`clients` 为空或为 null
		// `clients` 数组用于指定具体的 Feign 客户端类
		// “客户端类”指的是用 @FeignClient 注解标注的接口
		final Class<?>[] clients = attrs == null ? null
			: (Class<?>[]) attrs.get("clients");
		if (clients == null || clients.length == 0) {  // 如果 `clients` 为空，则意味着未指定具体类，需要基于包路径扫描找到候选类
			// 得到 包扫描的工具类 并将包含的规则设置到它的属性汇总
			// TODO 进入
			ClassPathScanningCandidateComponentProvider scanner = getScanner();
			// 设置资源加载器，用于加载资源和类文件
			scanner.setResourceLoader(this.resourceLoader);
			// 添加一个过滤器，仅包括带有 `@FeignClient` 注解的类
			scanner.addIncludeFilter(new AnnotationTypeFilter(FeignClient.class));
			// 获取 `basePackages` 集合，表示要扫描的基础包路径
			Set<String> basePackages = getBasePackages(metadata);
			// 遍历 `basePackages` 中的每个包路径，将扫描到的候选组件添加到 `candidateComponents`
			for (String basePackage : basePackages) {
				candidateComponents.addAll(scanner.findCandidateComponents(basePackage));
			}
		} else {
			// 如果指定了 `clients`，则直接将这些类作为候选组件添加到 `candidateComponents`
			for (Class<?> clazz : clients) {
				candidateComponents.add(new AnnotatedGenericBeanDefinition(clazz));
			}
		}
		// 遍历 `candidateComponents`，处理每个候选组件，确保是 `AnnotatedBeanDefinition`
		for (BeanDefinition candidateComponent : candidateComponents) {
			if (candidateComponent instanceof AnnotatedBeanDefinition) {
				// verify annotated class is an interface
				// 将 `candidateComponent` 转换为 `AnnotatedBeanDefinition`
				// 并获取其 `annotationMetadata`，用于读取注解元数据
				AnnotatedBeanDefinition beanDefinition = (AnnotatedBeanDefinition) candidateComponent;
				AnnotationMetadata annotationMetadata = beanDefinition.getMetadata();
				// 检查client是否是接口 不是抛异常
				Assert.isTrue(annotationMetadata.isInterface(),
					"@FeignClient can only be specified on an interface");
				// TODO 得到client @FeignCLient 注解信息
				Map<String, Object> attributes = annotationMetadata
					.getAnnotationAttributes(FeignClient.class.getCanonicalName());
				// 如果@FeignClient上有设置的话  得到client的name属性 标识该客户端名称
				String name = getClientName(attributes);
				// 如果@FeignClient上有设置的话  得到client的configuration属性  并注册到spring容器
				registerClientConfiguration(registry, name,
					attributes.get("configuration"));
				//client注册核心 TODO 进入
				registerFeignClient(registry, annotationMetadata, attributes);
			}
		}
	}

	private void registerFeignClient(BeanDefinitionRegistry registry,
									 AnnotationMetadata annotationMetadata, Map<String, Object> attributes) {
		String className = annotationMetadata.getClassName();
		/**
		 * 实际上往 spring容器 中 注册的是 FeignClientFactoryBean对象 即 factoryBean
		 * 并设置beanClass 为 client接口的class 查看FeignClientFactoryBean.getBean()方法 TODO
		 */
		Class clazz = ClassUtils.resolveClassName(className, null);
		ConfigurableBeanFactory beanFactory = registry instanceof ConfigurableBeanFactory
			? (ConfigurableBeanFactory) registry : null;
		String contextId = getContextId(beanFactory, attributes);
		String name = getName(attributes);
		FeignClientFactoryBean factoryBean = new FeignClientFactoryBean();
		factoryBean.setBeanFactory(beanFactory);
		factoryBean.setName(name);
		factoryBean.setContextId(contextId);
		factoryBean.setType(clazz);
		BeanDefinitionBuilder definition = BeanDefinitionBuilder
			.genericBeanDefinition(clazz, () -> {

				factoryBean.setUrl(getUrl(beanFactory, attributes));

				factoryBean.setPath(getPath(beanFactory, attributes));

				factoryBean.setDecode404(Boolean
					.parseBoolean(String.valueOf(attributes.get("decode404"))));

				Object fallback = attributes.get("fallback");
				if (fallback != null) {
					factoryBean.setFallback(fallback instanceof Class
						? (Class<?>) fallback
						: ClassUtils.resolveClassName(fallback.toString(), null));
				}

				Object fallbackFactory = attributes.get("fallbackFactory");
				if (fallbackFactory != null) {
					factoryBean.setFallbackFactory(fallbackFactory instanceof Class
						? (Class<?>) fallbackFactory
						: ClassUtils.resolveClassName(fallbackFactory.toString(),
						null));
				}
				return factoryBean.getObject();
			});
		definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
		definition.setLazyInit(true);
		validate(attributes);

		AbstractBeanDefinition beanDefinition = definition.getBeanDefinition();
		beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, className);
		beanDefinition.setAttribute("feignClientsRegistrarFactoryBean", factoryBean);

		// has a default, won't be null
		boolean primary = (Boolean) attributes.get("primary");

		beanDefinition.setPrimary(primary);

		String[] qualifiers = getQualifiers(attributes);
		if (ObjectUtils.isEmpty(qualifiers)) {
			qualifiers = new String[]{contextId + "FeignClient"};
		}

		BeanDefinitionHolder holder = new BeanDefinitionHolder(beanDefinition, className,
			qualifiers);
		// 将FeignClientFactoryBean 对象注册到容器
		BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);
	}

	/**
	 * 用于检查 @FeignClient 注解的属性配置，确保 fallback 和 fallbackFactory 属性的合法性。
	 *
	 * @param attributes
	 */
	private void validate(Map<String, Object> attributes) {
		// 将 `attributes`（`@FeignClient` 注解的属性映射）转换为 `AnnotationAttributes` 实例
		// `AnnotationAttributes` 是一种帮助类，可以方便地从映射中获取注解属性值
		AnnotationAttributes annotation = AnnotationAttributes.fromMap(attributes);
		// 这一行注释表示，如果某个别名属性被过度指定（例如设置了多于一个的别名值），此方法可能会抛出异常
		// This blows up if an aliased property is overspecified
		// FIXME annotation.getAliasedString("name", FeignClient.class, null);
		// 调用 `validateFallback` 方法来检查 `fallback` 属性的配置是否有效
		// `annotation.getClass("fallback")` 从 `@FeignClient` 注解中提取 `fallback` 属性的类信息
		validateFallback(annotation.getClass("fallback"));
		// 调用 `validateFallbackFactory` 方法来检查 `fallbackFactory` 属性的配置是否有效
		// `annotation.getClass("fallbackFactory")` 从 `@FeignClient` 注解中提取 `fallbackFactory` 属性的类信息
		validateFallbackFactory(annotation.getClass("fallbackFactory"));
	}

	/* for testing */ String getName(Map<String, Object> attributes) {
		return getName(null, attributes);
	}



	/**
	 * 定义一个方法用于解析字符串 `value`，支持 Spring 占位符和表达式解析
	 * 参数 `beanFactory` 提供对 BeanFactory 中表达式解析器的访问
	 *
	 * @param beanFactory
	 * @param value
	 * @return
	 */
	private String resolve(ConfigurableBeanFactory beanFactory, String value) {
		if (StringUtils.hasText(value)) {//  // 检查 `value` 是否非空且包含文本内容
			if (beanFactory == null) {//  // 如果 `beanFactory` 为 null，直接使用环境变量解析占位符
				return this.environment.resolvePlaceholders(value);// 使用环境变量解析占位符 `${...}`
			}
			// 获取 `BeanExpressionResolver`，用于解析 Spring 表达式语言 (SpEL) 表达式
			BeanExpressionResolver resolver = beanFactory.getBeanExpressionResolver();
			// 解析嵌入的占位符 `${...}`，将其替换为实际的值
			// `resolved` 变量保存替换后的结果字符串
			String resolved = beanFactory.resolveEmbeddedValue(value);
			if (resolver == null) {// 如果 `BeanExpressionResolver` 不存在，直接返回占位符解析结果
				return resolved;// 返回解析的字符串 `resolved`，不处理 SpEL 表达式
			}
			// 使用 `BeanExpressionResolver` 的 `evaluate` 方法进一步解析 SpEL 表达式 `#{...}`
			// 创建 `BeanExpressionContext` 提供 `beanFactory` 和作用域信息（这里作用域为 `null`）
			// 最终返回完全解析的字符串，包括占位符和 SpEL 表达式的解析结果
			return String.valueOf(resolver.evaluate(resolved,
				new BeanExpressionContext(beanFactory, null)));
		}
		return value;
	}

	private String getUrl(ConfigurableBeanFactory beanFactory,
						  Map<String, Object> attributes) {
		// TODO 进入
		String url = resolve(beanFactory, (String) attributes.get("url"));
		return getUrl(url);
	}

	private String getPath(ConfigurableBeanFactory beanFactory,
						   Map<String, Object> attributes) {
		// TODO 进入
		String path = resolve(beanFactory, (String) attributes.get("path"));
		return getPath(path);
	}

	/**
	 * 返回一个 ClassPathScanningCandidateComponentProvider 的实例，用于扫描类路径下符合条件的候选组件
	 *
	 * ClassPathScanningCandidateComponentProvider 是 Spring Framework 中的一个实用类，用于在类路径中扫描符合特定条件的候选组件（Bean 定义）。
	 * 它的主要功能是扫描类路径以找到符合条件的类或接口，并将它们作为候选组件注册到 Spring 容器中
	 * @return
	 */
	protected ClassPathScanningCandidateComponentProvider getScanner() {
		// 创建 `ClassPathScanningCandidateComponentProvider` 的匿名子类实例
		// 参数 `false` 表示禁用默认的 `TypeFilter`，仅使用自定义过滤器
		// `this.environment` 传递当前环境配置
		return new ClassPathScanningCandidateComponentProvider(false, this.environment) {
			// 重写 `isCandidateComponent` 方法，用于判断是否为候选组件
			@Override
			protected boolean isCandidateComponent(
				AnnotatedBeanDefinition beanDefinition) {
				// 初始化一个布尔值变量 `isCandidate` 为 `false`
				boolean isCandidate = false;
				// 检查当前 `beanDefinition` 是否是独立类
				// 独立类一般不依赖其他外部类即可实例化
				if (beanDefinition.getMetadata().isIndependent()) {
					// 如果 `beanDefinition` 不是注解类型，将 `isCandidate` 设置为 `true`
					if (!beanDefinition.getMetadata().isAnnotation()) {
						isCandidate = true;
					}
				}
				return isCandidate;
			}
		};
	}

	protected Set<String> getBasePackages(AnnotationMetadata importingClassMetadata) {
		// 从 `importingClassMetadata` 中获取 `EnableFeignClients` 注解的属性映射
		Map<String, Object> attributes = importingClassMetadata
			.getAnnotationAttributes(EnableFeignClients.class.getCanonicalName());
		// 创建一个空的 `Set` 集合 `basePackages`，用于存储基础包路径
		Set<String> basePackages = new HashSet<>();

		//  // 将非空的 `value` 属性添加到 `basePackages` 中
		for (String pkg : (String[]) attributes.get("value")) {
			if (StringUtils.hasText(pkg)) {
				basePackages.add(pkg);
			}
		}
		// 将非空的 `basePackages` 属性添加到 `basePackages` 集合中
		for (String pkg : (String[]) attributes.get("basePackages")) {
			if (StringUtils.hasText(pkg)) {
				basePackages.add(pkg);
			}
		}
		// 从 `basePackageClasses` 中获取每个类的包名，并添加到 `basePackages`
		for (Class<?> clazz : (Class[]) attributes.get("basePackageClasses")) {
			basePackages.add(ClassUtils.getPackageName(clazz));
		}
		// 如果未提供任何包信息，默认使用 `@EnableFeignClients` 注解所在类的包名
		if (basePackages.isEmpty()) {
			basePackages.add(
				ClassUtils.getPackageName(importingClassMetadata.getClassName()));
		}
		return basePackages;
	}

	private String getQualifier(Map<String, Object> client) {
		if (client == null) {// 如果 `client` 为 null，直接返回 null
			return null;
		}
		// 获取 `client` 中的 `qualifier` 属性值
		String qualifier = (String) client.get("qualifier");
		// 如果 `qualifier` 非空且包含文本内容，返回该值
		if (StringUtils.hasText(qualifier)) {
			return qualifier;
		}
		// 如果 `qualifier` 为空或没有文本内容，返回 null
		return null;
	}

	/**
	 * 从 client Map 中获取和处理 qualifiers，返回一个包含非空限定符的字符串数组
	 * @param client
	 * @return
	 */
	private String[] getQualifiers(Map<String, Object> client) {
		if (client == null) {// 如果 `client` 为 null，直接返回 null
			return null;
		}
		// 从 `client` 中提取 `qualifiers` 属性（字符串数组），并将其转换为 `ArrayList` 类型
		List<String> qualifierList = new ArrayList<>(
			Arrays.asList((String[]) client.get("qualifiers")));
		// 移除 `qualifierList` 中所有空字符串或仅包含空格的元素
		qualifierList.removeIf(qualifier -> !StringUtils.hasText(qualifier));
		// 如果 `qualifierList` 为空且 `getQualifier(client)` 有值，则将单个限定符添加到 `qualifierList`
		if (qualifierList.isEmpty() && getQualifier(client) != null) {
			qualifierList = Collections.singletonList(getQualifier(client));
		}
		// 如果 `qualifierList` 非空，将其转换为字符串数组并返回；否则返回 null
		return !qualifierList.isEmpty() ? qualifierList.toArray(new String[0]) : null;
	}

	/**
	 * 用于从 client Map 中按优先顺序获取 @FeignClient 的名称或上下文 ID（context ID）。如果 name 和 value 均未提供，则抛出异常
	 * @param client
	 * @return
	 */
	private String getClientName(Map<String, Object> client) {
		if (client == null) {// 如果 `client` 为 null，直接返回 null
			return null;
		}
		// 尝试从 `client` 中获取 `contextId` 属性
		String value = (String) client.get("contextId");
		// 如果 `contextId` 为空或仅包含空格，则尝试获取 `value` 属性
		if (!StringUtils.hasText(value)) {
			value = (String) client.get("value");
		}
		// 如果 `value` 为空或仅包含空格，则尝试获取 `name` 属性
		if (!StringUtils.hasText(value)) {
			value = (String) client.get("name");
		}
		// 如果 `name` 为空或仅包含空格，则尝试获取 `serviceId` 属性
		if (!StringUtils.hasText(value)) {
			value = (String) client.get("serviceId");
		}
		if (StringUtils.hasText(value)) {
			return value;
		}
		// 如果 `name` 或 `value` 都未提供，则抛出异常，表示缺少必须的属性
		throw new IllegalStateException("Either 'name' or 'value' must be provided in @"
			+ FeignClient.class.getSimpleName());
	}

	/**
	 * 在 Spring Bean 定义注册表（BeanDefinitionRegistry）中注册 FeignClientSpecification 的配置
	 * @param registry
	 * @param name
	 * @param configuration
	 */
	private void registerClientConfiguration(BeanDefinitionRegistry registry, Object name,
											 Object configuration) {
		// 使用 `BeanDefinitionBuilder` 创建 `FeignClientSpecification` 的 Bean 定义
		BeanDefinitionBuilder builder = BeanDefinitionBuilder
			.genericBeanDefinition(FeignClientSpecification.class);
		// 将 `name` 和 `configuration` 作为构造参数传递给 `FeignClientSpecification`
		builder.addConstructorArgValue(name);
		builder.addConstructorArgValue(configuration);
		// 在 `registry` 中注册以 `name` 命名的 Bean 定义，用于 `FeignClientSpecification` 的实例化
		registry.registerBeanDefinition(
			name + "." + FeignClientSpecification.class.getSimpleName(),
			builder.getBeanDefinition());
	}

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

}
