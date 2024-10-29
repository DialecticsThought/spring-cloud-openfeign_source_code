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

import java.util.Map;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;

import org.springframework.cloud.client.loadbalancer.LoadBalancedRetryFactory;
import org.springframework.cloud.netflix.ribbon.ServerIntrospector;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * Factory for SpringLoadBalancer instances that caches the entries created.
 *
 * @author Spencer Gibb
 * @author Dave Syer
 * @author Ryan Baxter
 * @author Gang Li
 */
public class CachingSpringLoadBalancerFactory {
	// ribbon相关 可以拿到 ILoadBalancer IClientConfig等等对象 这是ribbon整合feign关键
	protected final SpringClientFactory factory;
	// 带有充实策略的loadBalance工厂对象
	protected LoadBalancedRetryFactory loadBalancedRetryFactory = null;

	private volatile Map<String, FeignLoadBalancer> cache = new ConcurrentReferenceHashMap<>();

	public CachingSpringLoadBalancerFactory(SpringClientFactory factory) {
		this.factory = factory;
	}

	public CachingSpringLoadBalancerFactory(SpringClientFactory factory,
			LoadBalancedRetryFactory loadBalancedRetryPolicyFactory) {
		this.factory = factory;
		this.loadBalancedRetryFactory = loadBalancedRetryPolicyFactory;
	}

	/**
	 * TODO 重点 本质是工厂模式
	 *  CachingSpringLoadBalancerFactory是在FeignRibbonClientAutoConfiguration类中声明的
	 * @param clientName
	 * @return
	 */
	public FeignLoadBalancer create(String clientName) {
		FeignLoadBalancer client = this.cache.get(clientName);
		if (client != null) {
			return client;
		}
		IClientConfig config = this.factory.getClientConfig(clientName);
		// 得到 ribbon的ILoadBalancer对象 即ZoneAwareLoadBalancer对象的包装
		ILoadBalancer lb = this.factory.getLoadBalancer(clientName);
		ServerIntrospector serverIntrospector = this.factory.getInstance(clientName,
				ServerIntrospector.class);
		// 返回FeignLoadBalancer 实际上是对ZoneAwareLoadBalancer的包装实现负载均衡
		client = this.loadBalancedRetryFactory != null
				? new RetryableFeignLoadBalancer(lb, config, serverIntrospector,
						this.loadBalancedRetryFactory)
				: new FeignLoadBalancer(lb, config, serverIntrospector);
		// 把申城的FeignLoadBalancer对象缓存到map
		this.cache.put(clientName, client);
		return client;
	}

}
