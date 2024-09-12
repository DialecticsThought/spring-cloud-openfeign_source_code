/*
 * Copyright 2013-2020 the original author or authors.
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

import feign.Feign;
import feign.Target;

/**
 * @author Spencer Gibb
 */
class DefaultTargeter implements Targeter {

	@Override
	public <T> T target(FeignClientFactoryBean factory, Feign.Builder feign,
						FeignContext context, Target.HardCodedTarget<T> target) {
		/**
		 * <pre>
		 * TODO
		 *  进入 feign.Feign.Builder.target(feign.Target<T>)
		 *  最终就是调用ReflectiveFeign#newInstance()方法
		 *  这里使用的JDK的动态代理，因此，只要确定了InvocationHandler就可以了，最终的逻辑一定在InvocationHandler#invoke()方法中做的
		 *  这个方法一开始调用了feign.ReflectiveFeign.ParseHandlersByName#apply(feign.Target)方法 TODO 进入
		 *  apply方法里面最后调用了 feign.SynchronousMethodHandler.Factory#create方法
		 * 			=> 这个方法是 new ReflectiveFeign.FeignInvocationHandler(target, dispatch);
		 * 			=> 重点Map<Method, InvocationHandlerFactory.MethodHandler> dispatch 属性
		 *  Feign.Builder.build方法中有使用this.invocationHandlerFactory = new InvocationHandlerFactory.Default();
		 *  查看InvocationHandlerFactory类的内部的Default类
		 * </pre>
		 * <pre>
		 * TODO
		 *  SynchronousMethodHandler很重要  需要查看SynchronousMethodHandler#executeAndDecode()方法☆☆☆☆☆☆☆☆☆☆☆☆☆☆
		 *  这个方法里面有一个 执行response = this.client.execute(request, options);
		 *  这个client就是FeignClientFactoryBean.getTarget方法中传入的LoadBalancerFeignClient对象
		 *  查看LoadBalancerFeignClient.execute
		 * </pre>
		 * 注：
		 *
		 *
		 * ReflectiveFeign 做的工作就是为带有 @FeignClient 注解的接口，创建出接口方法的动态代理对象。
		 *  1.解析 FeignClient 接口上各个方法级别的注解，比如远程接口的 URL、接口类型（Get、Post 等）、各个请求参数等。
		 *  这里用到了 MVC Contract 协议解析，后面会讲到。
		 *  2.然后将解析到的数据封装成元数据，并为每一个方法生成一个对应的 MethodHandler 类作为方法级别的代理。相当于把服务的请求地址、接口类型等都帮我们封装好了。
		 *  这些 MethodHandler 方法会放到一个 HashMap 中。
		 *  3.然后会生成一个 InvocationHandler 用来管理这个 hashMap，其中 Dispatch 指向这个 HashMap。
		 *  4.然后使用 Java 的 JDK 原生的动态代理，实现了 FeignClient 接口的动态代理 Proxy 对象。这个 Proxy 会添加到 Spring 容器中。
		 *  5.当要调用接口方法时，其实会调用动态代理 Proxy 对象的 methodHandler 来发送请求。
		 */
		return feign.target(target);
	}

}
