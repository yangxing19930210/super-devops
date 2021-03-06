/*
 * Copyright 2017 ~ 2025 the original author or authors. <wanglsir@gmail.com, 983708408@qq.com>
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
package com.wl4g.devops.common.bean.iam.model;

import static com.wl4g.devops.common.utils.serialize.JacksonUtils.toJSONString;
import static java.util.Objects.nonNull;
import static org.springframework.util.CollectionUtils.isEmpty;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Sessions query model.
 * 
 * @author Wangl.sir &lt;Wanglsir@gmail.com, 983708408@qq.com&gt;
 * @version v1.0.0 2019-10-31
 * @since
 */
public class SessionModel implements Serializable {
	private static final long serialVersionUID = 1990530522326712114L;

	/**
	 * Active sessions response key-name.
	 */
	final public static String KEY_SESSIONS = "sessions";

	private String id;
	private Date startTimestamp;
	private Date stopTimestamp;
	private Date lastAccessTime;
	private long timeout;
	private boolean expired;
	private boolean authenticated;
	private String host;
	private Object principal;
	private Set<String> grantApplications = new HashSet<>();

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Date getStartTimestamp() {
		return startTimestamp;
	}

	public void setStartTimestamp(Date startTimestamp) {
		this.startTimestamp = startTimestamp;
	}

	public Date getStopTimestamp() {
		return stopTimestamp;
	}

	public void setStopTimestamp(Date stopTimestamp) {
		this.stopTimestamp = stopTimestamp;
	}

	public Date getLastAccessTime() {
		return lastAccessTime;
	}

	public void setLastAccessTime(Date lastAccessTime) {
		this.lastAccessTime = lastAccessTime;
	}

	public long getTimeout() {
		return timeout;
	}

	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	public boolean isExpired() {
		return expired;
	}

	public void setExpired(boolean expired) {
		this.expired = expired;
	}

	public boolean isAuthenticated() {
		return authenticated;
	}

	public void setAuthenticated(boolean authenticated) {
		this.authenticated = authenticated;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public Object getPrincipal() {
		return principal;
	}

	public void setPrincipal(Object principal) {
		if (nonNull(principal)) {
			this.principal = principal;
		}
	}

	public Set<String> getGrantApplications() {
		return grantApplications;
	}

	public void setGrantApplications(Set<String> grantApplications) {
		if (!isEmpty(grantApplications)) {
			this.grantApplications.addAll(grantApplications);
		}
	}

	@Override
	public String toString() {
		return toJSONString(this);
	}

}
