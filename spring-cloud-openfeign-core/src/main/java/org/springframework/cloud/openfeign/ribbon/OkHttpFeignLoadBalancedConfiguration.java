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

package org.springframework.cloud.openfeign.ribbon;

import feign.Client;
import feign.okhttp.OkHttpClient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.cloud.openfeign.clientconfig.OkHttpFeignConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author Spencer Gibb
 * @author Olga Maciaszek-Sharma
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(OkHttpClient.class)// 表示此时在Spring容器中没有Client实现类bean对象，也就是程序员没有自动在某个配置类中声明一个Client的实现类(一般我们也不会这么做)
@ConditionalOnProperty("feign.okhttp.enabled")// 表示引入了OkHttp相关的依赖，才会有OkHttpClient.class
@Import(OkHttpFeignConfiguration.class)
class OkHttpFeignLoadBalancedConfiguration {

	/**
	 *
	 * @param cachingFactory  TODO 进入
	 * @param clientFactory 类似FeignContext，是ribbon获取其组件的一个上下文 这两个类的父类都是NamedContextFactory
	 * @param okHttpClient  真正发起远程调用的的http 客户端
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(Client.class)
	public Client feignClient(CachingSpringLoadBalancerFactory cachingFactory,
			SpringClientFactory clientFactory, okhttp3.OkHttpClient okHttpClient) {
		OkHttpClient delegate = new OkHttpClient(okHttpClient);
		return new LoadBalancerFeignClient(delegate, cachingFactory, clientFactory);
	}

}
