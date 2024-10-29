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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

/**
 * Annotation for interfaces declaring that a REST client with that interface should be
 * created (e.g. for autowiring into another component). If ribbon is available it will be
 * used to load balance the backend requests, and the load balancer can be configured
 * using a <code>@RibbonClient</code> with the same name (i.e. value) as the feign client.
 *
 * @author Spencer Gibb
 * @author Venil Noronha
 * @author Olga Maciaszek-Sharma
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface FeignClient {

	/**
	 * TODO
	 * 	value 用于指定服务的名称，也可以包含可选的协议前缀（例如 http:// 或 https://）
	 *    @FeignClient(value = "user-service") 会将客户端名称设置为 user-service。
	 *
	 * The name of the service with optional protocol prefix. Synonym for {@link #name()
	 * name}. A name must be specified for all clients, whether or not a url is provided.
	 * Can be specified as property key, eg: ${propertyKey}.
	 *
	 * @return the name of the service with optional protocol prefix
	 */
	@AliasFor("name") // TODO 人话 就是value和name都可以 因为是别名
		String value() default "";

	/**
	 * TODO 这是 value 属性的同义词，允许指定服务 ID，但已被废弃
	 * The service id with optional protocol prefix. Synonym for {@link #value() value}.
	 *
	 * @return the service id with optional protocol prefix
	 * @deprecated use {@link #name() name} instead
	 */
	@Deprecated
	String serviceId() default "";

	/**
	 * TODO
	 * 	说明：定义 Feign 客户端 Bean 的名称，优先级高于 name，但不会作为服务 ID 使用。
	 * 	作用：用于在 Spring 容器中生成 Bean 时指定 Bean 的 ID，主要用于在同一服务下使用多个客户端 Bean 以避免 Bean 名称冲突
	 * 	示例：@FeignClient(contextId = "userServiceClient", name = "user-service") 将 userServiceClient 用作 Bean 名称，而 user-service 作为服务名。
	 * This will be used as the bean name instead of name if present, but will not be used
	 * as a service id.
	 *
	 * @return bean name instead of name if present
	 */
	String contextId() default "";

	/**
	 * TODO
	 * 	说明：用于定义服务名称，这是 value 属性的同义词。
	 * 	作用：类似于 value，用于指定服务的逻辑名称或服务 ID。Spring 推荐使用 name 代替 value 和 serviceId。
	 * 	示例：@FeignClient(name = "order-service") 指定服务名为 order-service。
	 *
	 * @return The service id with optional protocol prefix. Synonym for {@link #value()
	 * value}.
	 */
	@AliasFor("value")
	String name() default "";

	/**
	 * TODO
	 * 	说明：这是 @Qualifier 注解的值，为 Feign 客户端指定限定符，但已被废弃。
	 * 	作用：用于为该 Feign 客户端 Bean 指定限定符，确保 Spring 在注入时可以精确识别此 Bean 实例。如果同时设置了 qualifier 和 qualifiers，优先使用 qualifiers。
	 * 	标记：已被废弃，建议改用 qualifiers() 属性（未在此代码段中显示）。
	 *
	 * @return the <code>@Qualifier</code> value for the feign client.
	 * @deprecated in favour of {@link #qualifiers()}.
	 * <p>
	 * If both {@link #qualifier()} and {@link #qualifiers()} are present, we will use the
	 * latter, unless the array returned by {@link #qualifiers()} is empty or only
	 * contains <code>null</code> or whitespace values, in which case we'll fall back
	 * first to {@link #qualifier()} and, if that's also not present, to the default =
	 * <code>contextId + "FeignClient"</code>.
	 */
	@Deprecated
	String qualifier() default "";

	/**
	 *
	 *	<pre>
	 *	 TODO
	 *	  你有两个不同的客户端类 UserServiceClient 和 UserServiceClientV2，它们都指向相同的 user-service 服务，
	 *	  但由于客户端配置不同，你希望将它们分别作为不同的 Bean 注册并加以区分
	 *		 @FeignClient(name = "user-service", qualifiers = {"userClientV1"})
	 * 		  public interface UserServiceClient {
	 *     	 	@GetMapping("/users/{id}")
	 *     	 	User getUserById(@PathVariable("id") Long id);
	 * 		 }
	 * 		 @FeignClient(name = "user-service", qualifiers = {"userClientV2"})
	 * 		 public interface UserServiceClientV2 {
	 *     	 	@GetMapping("/v2/users/{id}")
	 *     	 	User getUserById(@PathVariable("id") Long id);
	 * 		 }
	 * 		 使用 qualifiers 进行注入
	 * 		 两个客户端类都注册为了 user-service 服务的 Feign 客户端，但在 Spring 容器中是不同的 Bean（分别带有限定符 userClientV1 和 userClientV2）
	 * 		 这样在注入时，可以根据需要指定某一个限定符，从而选择合适的客户端 Bean
	 * 		 @Autowired
	 * 		 @Qualifier("userClientV1")
	 * 		 private UserServiceClient userServiceClient;
	 *
	 * 		 @Autowired
	 * 		 @Qualifier("userClientV2")
	 * 		 private UserServiceClientV2 userServiceClientV2;
	 *	</pre>
	 * @return the <code>@Qualifiers</code> value for the feign client.
	 * <p>
	 * If both {@link #qualifier()} and {@link #qualifiers()} are present, we will use the
	 * latter, unless the array returned by {@link #qualifiers()} is empty or only
	 * contains <code>null</code> or whitespace values, in which case we'll fall back
	 * first to {@link #qualifier()} and, if that's also not present, to the default =
	 * <code>contextId + "FeignClient"</code>.
	 */
	String[] qualifiers() default {};

	/**
	 * TODO 类似@RequestMapping声明的base url
	 *
	 * @return an absolute URL or resolvable hostname (the protocol is optional).
	 */
	String url() default "";

	/**
	 * @return whether 404s should be decoded instead of throwing FeignExceptions
	 */
	boolean decode404() default false;

	/**
	 * TODO  client的配置，不同于@EnableFeignClients的defaultConfiguration，这个configuration只属于某个client
	 * A custom configuration class for the feign client. Can contain override
	 * <code>@Bean</code> definition for the pieces that make up the client, for instance
	 * {@link feign.codec.Decoder}, {@link feign.codec.Encoder}, {@link feign.Contract}.
	 *
	 * @return list of configurations for feign client
	 * @see FeignClientsConfiguration for the defaults
	 */
	Class<?>[] configuration() default {};

	/**
	 * TODO 降级处理的实例，需实现FeignClient
	 * <p>
	 * Fallback class for the specified Feign client interface. The fallback class must
	 * implement the interface annotated by this annotation and be a valid spring bean.
	 *
	 * @return fallback class for the specified Feign client interface
	 */
	Class<?> fallback() default void.class;

	/**
	 * TODO
	 * 	降级处理实例的创建工厂，需实现FallbackFactory接口 推荐
	 * 	本质是写一个标注了@FeignClient接口的实现类，实现类的每一个接口实现方法都是降级操作的方法
	 * <p>
	 * Define a fallback factory for the specified Feign client interface. The fallback
	 * factory must produce instances of fallback classes that implement the interface
	 * annotated by {@link FeignClient}. The fallback factory must be a valid spring bean.
	 *
	 * @return fallback factory for the specified Feign client interface
	 * @see feign.hystrix.FallbackFactory for details.
	 * @see FallbackFactory for details.
	 */
	Class<?> fallbackFactory() default void.class;

	/**
	 * @return path prefix to be used by all method-level mappings. Can be used with or
	 * without <code>@RibbonClient</code>.
	 */
	String path() default "";

	/**
	 * @return whether to mark the feign proxy as a primary bean. Defaults to true.
	 */
	boolean primary() default true;

}
