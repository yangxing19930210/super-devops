/*
 * Copyright 2017 ~ 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wl4g.devops.iam.common.utils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.shiro.util.StringUtils;
import org.springframework.util.Assert;

/**
 * Session binding utility
 * 
 * @author Wangl.sir <983708408@qq.com>
 * @version v1.0
 * @date 2018年12月21日
 * @since
 */
public abstract class SessionBindings extends Sessions {

	final private static String KEY_SESSION_ATTR_TTL_PREFIX = "attributeTTL_";

	/**
	 * Whether the comparison with the target is equal from the session
	 * attribute-Map
	 * 
	 * @param sessionKey
	 *            Keys to save and session
	 * @param target
	 *            Target object
	 * @param unbind
	 *            Whether to UN-bundle
	 * @return Is there a value in session that matches the target
	 */
	public static boolean withIn(String sessionKey, Object target, boolean unbind) {
		try {
			return withIn(sessionKey, target);
		} finally {
			unbind(sessionKey);
		}
	}

	/**
	 * Whether the comparison with the target is equal from the session
	 * attribute-Map
	 * 
	 * @param sessionKey
	 *            Keys to save and session
	 * @param target
	 *            Target object
	 * @return Is there a value in session that matches the target
	 */
	public static boolean withIn(String sessionKey, Object target) {
		Assert.notNull(sessionKey, "'sessionKey' must not be null");
		Assert.notNull(target, "'target' must not be null");

		Object sessionValue = getBindValue(sessionKey);
		if (sessionValue != null) {
			if ((sessionValue instanceof String) && (target instanceof String)) { // String
				return String.valueOf(sessionValue).equalsIgnoreCase(String.valueOf(target));
			} else if ((sessionValue instanceof Enum) || (target instanceof Enum)) { // ENUM
				return (sessionValue == target || sessionValue.toString().equalsIgnoreCase(target.toString()));
			} else { // Other object
				return sessionValue == target;
			}
		}
		return false;
	}

	/**
	 * Get bind of session value
	 * 
	 * @param sessionKey
	 *            Keys to save and session
	 * @param unbind
	 *            Whether to UN-bundle
	 * @return
	 */
	public static <T> T getBindValue(String sessionKey, boolean unbind) {
		try {
			return getBindValue(sessionKey);
		} finally {
			unbind(sessionKey);
		}
	}

	/**
	 * Get bind of session value
	 * 
	 * @param sessionKey
	 *            Keys to save and session
	 * @return
	 */
	/**
	 * @param sessionKey
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getBindValue(String sessionKey) {
		Assert.notNull(sessionKey, "'sessionKey' must not be null");
		// Get bind value.
		T value = (T) getSession().getAttribute(sessionKey);
		// Get value TTL.
		SessionAttrTTL ttl = (SessionAttrTTL) getSession().getAttribute(getExpireKey(sessionKey));
		if (ttl != null) { // Need to check expiration
			if ((System.currentTimeMillis() - ttl.getCreateTime()) >= ttl.getExpireMs()) { // Expired?
				unbind(sessionKey); // Cleanup
				return null; // Because it's expired.
			}
		}
		return value;
	}

	/**
	 * Extract key value parameters
	 * 
	 * @param sessionKey
	 * @param paramKey
	 * @return
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T> T extParameterValue(String sessionKey, String paramKey) {
		Assert.notNull(sessionKey, "'sessionKey' must not be null");
		Assert.notNull(paramKey, "'paramKey' must not be null");

		// Extract parameter
		Map parameter = (Map) getBindValue(sessionKey);
		if (parameter != null) {
			return (T) parameter.get(paramKey);
		}
		return null;
	}

	/**
	 * Bind value to session
	 * 
	 * @param sessionKey
	 * @param keyValues
	 */
	public static void bindKVParameters(String sessionKey, Object[] keyValues) {
		Assert.notNull(sessionKey, "'sessionKey' must not be null");
		Assert.notEmpty(keyValues, "'keyValues' must not be null");
		Assert.isTrue(keyValues.length % 2 == 0, "Illegal 'keyValues' length");

		// Extract key values
		Map<Object, Object> paramster = new HashMap<>();
		for (int i = 0; i < keyValues.length - 1; i++) {
			if (i % 2 == 0) {
				Object key = keyValues[i];
				Object value = keyValues[i + 1];
				if (key != null && StringUtils.hasText(key.toString()) && value != null
						&& StringUtils.hasText(value.toString())) {
					paramster.put(key, value);
				}
			}
		}

		// Binding
		bind(sessionKey, paramster);
	}

	/**
	 * Bind value to session
	 * 
	 * @param sessionKey
	 * @param value
	 * @param expireMs
	 * @return
	 */
	public static <T> T bind(String sessionKey, T value, long expireMs) {
		bind(sessionKey, value);
		bind(getExpireKey(sessionKey), new SessionAttrTTL(expireMs));
		return value;
	}

	/**
	 * Bind value to session
	 * 
	 * @param sessionKey
	 * @param value
	 */
	public static <T> T bind(String sessionKey, T value) {
		Assert.notNull(sessionKey, "'sessionKey' must not be null");
		getSession().setAttribute(sessionKey, value);
		return value;
	}

	/**
	 * UN-bind sessionKey of session
	 * 
	 * @param sessionKey
	 * @return
	 */
	public static boolean unbind(String sessionKey) {
		Assert.notNull(sessionKey, "'sessionKey' must not be null");
		getSession().removeAttribute(getExpireKey(sessionKey)); // TTL-attribute?
		return getSession().removeAttribute(sessionKey) != null;
	}

	/**
	 * Get expire key.
	 * 
	 * @param sessionKey
	 * @return
	 */
	private static String getExpireKey(String sessionKey) {
		Assert.hasText(sessionKey, "'sessionKey' must not be empty");
		return KEY_SESSION_ATTR_TTL_PREFIX + sessionKey;
	}

	/**
	 * Session attribute timeToLive model
	 * 
	 * @author Wangl.sir
	 * @version v1.0 2019年8月23日
	 * @since
	 */
	public static class SessionAttrTTL implements Serializable {
		private static final long serialVersionUID = -5108678535942593956L;

		final private Long createTime;

		final private Long expireMs;

		public SessionAttrTTL(Long expireMs) {
			this(System.currentTimeMillis(), expireMs);
		}

		public SessionAttrTTL(Long createTime, Long expireMs) {
			Assert.state(createTime != null, "'createTime' must not be null.");
			Assert.state(expireMs != null, "'expireMs' must not be null.");
			this.createTime = createTime;
			this.expireMs = expireMs;
		}

		public Long getCreateTime() {
			return createTime;
		}

		public Long getExpireMs() {
			return expireMs;
		}

	}

}