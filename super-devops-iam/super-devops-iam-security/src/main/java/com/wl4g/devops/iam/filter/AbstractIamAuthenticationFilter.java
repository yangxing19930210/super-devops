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
package com.wl4g.devops.iam.filter;

import static com.wl4g.devops.common.constants.IAMDevOpsConstants.BEAN_DELEGATE_MSG_SOURCE;
import static com.wl4g.devops.common.constants.IAMDevOpsConstants.KEY_ERR_SESSION_SAVED;
import static com.wl4g.devops.common.constants.IAMDevOpsConstants.KEY_SERVICE_ROLE;
import static com.wl4g.devops.common.constants.IAMDevOpsConstants.KEY_SERVICE_ROLE_VALUE_IAMSERVER;
import static com.wl4g.devops.common.constants.IAMDevOpsConstants.URI_AUTH_BASE;
import static com.wl4g.devops.common.utils.Exceptions.getRootCausesString;
import static com.wl4g.devops.common.utils.serialize.JacksonUtils.toJSONString;
import static com.wl4g.devops.common.utils.web.WebUtils2.applyQueryURL;
import static com.wl4g.devops.common.utils.web.WebUtils2.cleanURI;
import static com.wl4g.devops.common.utils.web.WebUtils2.getBaseURIForDefault;
import static com.wl4g.devops.common.utils.web.WebUtils2.getHttpRemoteAddr;
import static com.wl4g.devops.common.utils.web.WebUtils2.getRFCBaseURI;
import static com.wl4g.devops.common.utils.web.WebUtils2.safeEncodeURL;
import static com.wl4g.devops.common.utils.web.WebUtils2.toQueryParams;
import static com.wl4g.devops.common.utils.web.WebUtils2.writeJson;
import static com.wl4g.devops.common.web.RespBase.RetCode.OK;
import static com.wl4g.devops.common.web.RespBase.RetCode.UNAUTHC;
import static com.wl4g.devops.iam.common.utils.Securitys.SESSION_STATUS_AUTHC;
import static com.wl4g.devops.iam.common.utils.Securitys.SESSION_STATUS_UNAUTHC;
import static com.wl4g.devops.iam.common.utils.Securitys.correctAuthenticaitorURI;
import static com.wl4g.devops.iam.common.utils.SessionBindings.bind;
import static com.wl4g.devops.iam.common.utils.SessionBindings.bindKVParameters;
import static com.wl4g.devops.iam.common.utils.SessionBindings.extParameterValue;
import static com.wl4g.devops.iam.common.utils.SessionBindings.unbind;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.apache.shiro.util.Assert.hasText;
import static org.apache.shiro.web.util.WebUtils.getCleanParam;
import static org.apache.shiro.web.util.WebUtils.issueRedirect;
import static org.apache.shiro.web.util.WebUtils.toHttp;
import static org.springframework.util.Assert.notNull;
import static org.springframework.util.CollectionUtils.isEmpty;

import com.wl4g.devops.common.exception.iam.AccessRejectedException;
import com.wl4g.devops.common.exception.iam.IamException;
import com.wl4g.devops.common.utils.web.WebUtils2;
import com.wl4g.devops.common.utils.web.WebUtils2.ResponseType;
import com.wl4g.devops.common.web.RespBase;
import com.wl4g.devops.iam.common.authc.IamAuthenticationToken;
import com.wl4g.devops.iam.common.authc.IamAuthenticationToken.RedirectInfo;
import com.wl4g.devops.iam.common.cache.EnhancedCacheManager;
import com.wl4g.devops.iam.common.filter.IamAuthenticationFilter;
import com.wl4g.devops.iam.common.i18n.SessionDelegateMessageBundle;
import com.wl4g.devops.iam.config.properties.IamProperties;
import com.wl4g.devops.iam.configure.ServerSecurityConfigurer;
import com.wl4g.devops.iam.configure.ServerSecurityCoprocessor;
import com.wl4g.devops.iam.handler.AuthenticationHandler;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Multiple channel login authentication submitted processing based filter
 * 
 * {@link org.apache.shiro.web.filter.mgt.DefaultFilterChainManager#proxy()}
 * {@link org.apache.shiro.web.filter.mgt.PathMatchingFilterChainResolver#pathMatches()}
 * {@link org.apache.shiro.web.filter.mgt.PathMatchingFilterChainResolver#getChain()}
 * {@link org.apache.shiro.web.servlet.AbstractShiroFilter#getExecutionChain()}
 * {@link org.apache.shiro.web.servlet.AbstractShiroFilter#executeChain()}
 * 
 * @author Wangl.sir <983708408@qq.com>
 * @version v1.0
 * @date 2018年11月27日
 * @since
 */
public abstract class AbstractIamAuthenticationFilter<T extends IamAuthenticationToken> extends AuthenticatingFilter
		implements IamAuthenticationFilter {

	/**
	 * Login/Authentication request parameters binding session key
	 */
	final public static String KEY_REQ_AUTH_PARAMS = AbstractIamAuthenticationFilter.class.getSimpleName() + ".REQ_AUTH_PARAMS";

	/**
	 * Authentication request redirect information key.
	 */
	final public static String KEY_REQ_AUTH_REDIRECT = "REQ_AUTH_REDIRECT";

	/**
	 * URI login submission base path for processing all SHIRO authentication
	 * filters submitted by login
	 */
	final public static String URI_BASE_MAPPING = URI_AUTH_BASE;

	final protected Logger log = LoggerFactory.getLogger(getClass());

	/**
	 * IAM server configuration properties
	 */
	@Autowired
	protected IamProperties config;

	/**
	 * IAM authentication handler
	 */
	@Autowired
	protected AuthenticationHandler authHandler;

	/**
	 * IAM security configure handler
	 */
	@Autowired
	protected ServerSecurityConfigurer configurer;

	/**
	 * IAM server security processor
	 */
	@Autowired
	protected ServerSecurityCoprocessor coprocessor;

	/**
	 * Enhanced cache manager.
	 */
	@Autowired
	protected EnhancedCacheManager cacheManager;

	/**
	 * Delegate message source.
	 */
	@Resource(name = BEAN_DELEGATE_MSG_SOURCE)
	protected SessionDelegateMessageBundle bundle;

	@Override
	protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) {
		/*
		 * The request path must be /login/general or /login/sms, etc. Just
		 * return to false directly, that is, let it execute
		 * this#onAccessDenied()
		 */
		return false;
	}

	@Override
	protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
		if (log.isDebugEnabled()) {
			log.debug("Access denied url: {}", toHttp(request).getRequestURI());
		}
		return executeLogin(request, response);
	}

	@Override
	protected IamAuthenticationToken createToken(ServletRequest request, ServletResponse response) throws Exception {
		/*
		 * Pre-handler before authentication, For example, the implementation of
		 * restricting client IP white-list to prevent violent cracking of large
		 * number of submission login requests.
		 */
		if (!coprocessor.preAuthentication(this, request, response)) {
			throw new AccessRejectedException(
					String.format("Access rejected for remote IP:%s", WebUtils2.getHttpRemoteAddr(toHttp(request))));
		}

		// Remote client host.
		String remoteHost = getHttpRemoteAddr(toHttp(request));
		// Redirection.
		RedirectInfo redirect = getRedirectInfo(request, false);

		/**
		 * Remember request protocol parameters.</br>
		 * e.g. Android submit login will bring redirectUrl and application,
		 * which will be used for successful login redirection.
		 */
		rememberProtocolParameters(redirect, request, response);

		// Create authentication token
		return postCreateToken(remoteHost, redirect, toHttp(request), toHttp(response));
	}

	protected abstract T postCreateToken(String remoteHost, RedirectInfo redirectInfo, HttpServletRequest request,
			HttpServletResponse response) throws Exception;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	protected boolean onLoginSuccess(AuthenticationToken token, Subject subject, ServletRequest request, ServletResponse response)
			throws Exception {
		try {
			IamAuthenticationToken tk = (IamAuthenticationToken) token;

			/*
			 * Save the token at the time of authentication, which can then be
			 * used for extended logic usage.
			 */
			// bind(KEY_AUTHC_TOKEN, tk);

			// Get redirection.
			RedirectInfo redirect = getRedirectInfo(request, true);
			if (isBlank(redirect.getFromAppName())) { // Use default?
				redirect.setFromAppName(config.getSuccessService());
				redirect.setRedirectUrl(getSuccessUrl());
			}

			// Determine redirectUrl(authenticator URL).
			String successRedirectUrl = correctAuthenticaitorURI(
					determineSuccessUrl(tk, subject, request, response, redirect.getRedirectUrl()));
			redirect.setRedirectUrl(successRedirectUrl);
			hasText(redirect.getFromAppName(), "Successful redirect application must not be empty.");
			hasText(redirect.getRedirectUrl(), "Successful redirect URL must not be empty.");

			// Granting ticket.
			String grantTicket = authHandler.loggedin(redirect.getFromAppName(), subject).getGrantTicket();

			// Build response parameter.
			Map params = new HashMap();
			params.put(config.getParam().getApplication(), redirect.getFromAppName());

			// Response with JSON?
			if (isJSONResponse(request)) {
				try {
					// Placing it in http.body makes it easier for Android/iOS
					// to get token.
					params.put(config.getCookie().getName(), subject.getSession().getId());
					// Make logged JSON.
					RespBase<String> loggedResp = makeLoggedResponse(request, grantTicket, redirect.getRedirectUrl(), params);

					// Handle success processing.
					coprocessor.postAuthenticatingSuccess(tk, subject, request, response, loggedResp.getData());

					String logged = toJSONString(loggedResp);
					if (log.isInfoEnabled()) {
						log.info("Response to success - {}", logged);
					}
					writeJson(toHttp(response), logged);
				} catch (IOException e) {
					log.error("Login success response json error", e);
				}
			} else { // Redirection page?
				/*
				 * When the source application exists, the indication is that it
				 * needs to be redirected to the CAS client application, then
				 * grantTicket is required.
				 */
				if (isNotBlank(grantTicket)) {
					params.put(config.getParam().getGrantTicket(), grantTicket);
				}

				// Handle success processing.
				coprocessor.postAuthenticatingSuccess(tk, subject, request, response, params);

				if (log.isInfoEnabled()) {
					log.info("Redirect to successUrl '{}', param:{}", redirect.getRedirectUrl(), params);
				}
				issueRedirect(request, response, redirect.getRedirectUrl(), params, true);
			}

		} catch (IamException e) {
			// Convert to authentication exception before it can be intercepted.
			// See:org.apache.shiro.web.filter.authc.AuthenticatingFilter#executeLogin
			throw new AuthenticationException(e);
		} finally {
			cleanup(token, subject, request, response); // Clean-up
		}

		// Redirection has been responded and no further execution is required.
		return false;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	protected boolean onLoginFailure(AuthenticationToken token, AuthenticationException ae, ServletRequest request,
			ServletResponse response) {
		IamAuthenticationToken tk = (IamAuthenticationToken) token;

		String errmsg = getRootCausesString(ae);
		if (isNotBlank(errmsg)) {
			log.error("Failed to authentication.", ae);
			if (log.isDebugEnabled()) {
				log.debug("Failed to authentication.", ae);
			} else {
				log.warn("Failed to authentication. caused by: ", errmsg);
			}
			/**
			 * {@link LoginAuthenticatorController#errReads()}
			 */
			bind(KEY_ERR_SESSION_SAVED, errmsg);
		}
		// Failure redirect URI
		String failRedirectUrl = determineFailureUrl(tk, ae, request, response);

		// Post handling of authentication failure.
		coprocessor.postAuthenticatingFailure(tk, ae, request, response);

		// Obtain bound parameters.
		Map params = new HashMap();
		RedirectInfo redirect = getRedirectInfo(request, false);
		params.put(config.getParam().getApplication(), redirect.getFromAppName());
		params.put(config.getParam().getRedirectUrl(), redirect.getRedirectUrl());

		// Response JSON message.
		if (isJSONResponse(request)) {
			try {
				String failed = makeFailedResponse(failRedirectUrl, request, params, errmsg);
				if (log.isInfoEnabled()) {
					log.info("Response unauthentication. {}", failed);
				}
				writeJson(toHttp(response), failed);
			} catch (IOException e) {
				log.error("Response unauthentication json error", e);
			}
		}
		// Redirects the login page directly.
		else {
			try {
				if (log.isInfoEnabled()) {
					log.info("Redirect to login: {}", failRedirectUrl);
				}
				issueRedirect(request, response, failRedirectUrl, params, true);
			} catch (IOException e1) {
				log.error("Redirect to login failed.", e1);
			}
		}

		// Redirection has been responded and no further execution is required.
		return false;
	}

	/**
	 * Determine is the JSON interactive strategy, or get it from
	 * session(flexible API).
	 * 
	 * @param request
	 * @return
	 */
	protected boolean isJSONResponse(ServletRequest request) {
		// Using last saved parameters.
		String respTypeValue = extParameterValue(KEY_REQ_AUTH_PARAMS, ResponseType.DEFAULT_RESPTYPE_NAME);
		if (log.isDebugEnabled()) {
			log.debug("Using last response type:{}", respTypeValue);
		}
		return ResponseType.isJSONResponse(respTypeValue, toHttp(request)) || ResponseType.isJSONResponse(toHttp(request));
	}

	/**
	 * Get redirect information bound from authentication request
	 * {@link AuthenticatorAuthenticationFilter#rememberProtocolParameters(ServletRequest, ServletResponse)}
	 * </br>
	 * {@link AbstractIamAuthenticationFilter#createToken(ServletRequest, ServletResponse)}
	 * 
	 * @param request
	 * @param bindOnly
	 *            Whether to get only the redirection information of the binding
	 * @return
	 */
	protected RedirectInfo getRedirectInfo(ServletRequest request, boolean bindOnly) {
		RedirectInfo redirect = null;
		if (bindOnly) {
			redirect = extParameterValue(KEY_REQ_AUTH_PARAMS, KEY_REQ_AUTH_REDIRECT);
		} else {
			redirect = new RedirectInfo(getCleanParam(request, config.getParam().getApplication()),
					getCleanParam(request, config.getParam().getRedirectUrl()));
			if (!redirect.isValidity()) {
				redirect = extParameterValue(KEY_REQ_AUTH_PARAMS, KEY_REQ_AUTH_REDIRECT);
			}
		}
		return isNull(redirect) ? RedirectInfo.EMPTY : redirect;
	}

	/**
	 * Saved the latest request protocol configuration, such as response_type,
	 * source application, etc.</br>
	 * E.G.:</br>
	 * </br>
	 * 
	 * <b>Req1：</b>http://localhost:14040/iam-server/view/login.html?service=iam-example&redirect_url=http://localhost:14041/iam-example/index.html</br>
	 * <b>Resp1：</b>login.html</br>
	 * </br>
	 * <b>Req2：(Intercepted by
	 * rootFilter)</b>http://localhost:14040/iam-server/favicon.ico</br>
	 * <b>Resp2：</b>
	 * 302->http://localhost:14040/iam-server/view/login.html?service=iam-example&redirect_url=http://localhost:14041/iam-example/index.html</br>
	 * </br>
	 * <b>Req3：</b>http://localhost:14040/iam-server/view/login.html</br>
	 * </br>
	 * 
	 * No parameters for the second request for login.html ??? This is the
	 * problem to be solved by this method.
	 * 
	 * @param redirect
	 * @param request
	 * @param response
	 */
	protected void rememberProtocolParameters(RedirectInfo redirect, ServletRequest request, ServletResponse response) {
		notNull(redirect, "Redirect info must not be null.");
		// Safety encoding for URL fragment.
		redirect.setRedirectUrl(safeEncodeHierarchyRedirectUrl(redirect.getRedirectUrl()));

		// Response type.
		String respTypeKey = ResponseType.DEFAULT_RESPTYPE_NAME;
		String respType = getCleanParam(request, respTypeKey);

		// Overlay to save the latest parameters.
		bindKVParameters(KEY_REQ_AUTH_PARAMS, respTypeKey, respType, KEY_REQ_AUTH_REDIRECT, redirect);
		if (log.isDebugEnabled()) {
			log.debug("Binding for respType[{}], redirect[{}]", respType, redirect);
		}

	}

	/**
	 * The redirection URI of the secure encoding loop is mainly used to prevent
	 * the loss of fragments such as for example "/#/index".
	 * 
	 * </br>
	 * e.g.
	 * 
	 * <pre>
	 * http://mydomain.com/iam-example/authenticator?redirect_url=http://mydomain.com/#/index
	 * => http://mydomain.com/iam-example/authenticator?redirect_url=http%3A%2F%2Fmydomain.com%2F%23%2Findex
	 * </pre>
	 * 
	 * @param redirectUrl
	 * @return
	 */
	protected String safeEncodeHierarchyRedirectUrl(String redirectUrl) {
		if (!isBlank(redirectUrl)) {
			// To prevent automatic loss, such as the anchor part of "#".
			URI uri = URI.create(redirectUrl);
			Map<String, String> params = toQueryParams(uri.getQuery() + "#" + uri.getFragment());
			if (!isEmpty(params)) {
				String clientRedirectUrl = params.get(config.getParam().getRedirectUrl());
				if (!isBlank(clientRedirectUrl)) {
					params.remove(config.getParam().getRedirectUrl());
					params.put(config.getParam().getRedirectUrl(), safeEncodeURL(clientRedirectUrl));
					String newRedirectUrl = getBaseURIForDefault(uri.getScheme(), uri.getHost(), uri.getPort()) + uri.getPath();
					redirectUrl = applyQueryURL(newRedirectUrl, params);
				}
			}
		}
		return redirectUrl;
	}

	/**
	 * Cleaning-up.(e.g.Save request parameters)
	 * 
	 * @param token
	 * @param subject
	 * @param request
	 * @param response
	 */
	private void cleanup(AuthenticationToken token, Subject subject, ServletRequest request, ServletResponse response) {
		/*
		 * Clean See:AuthenticatorAuthenticationFilter#bindRequestParameters()
		 */
		unbind(KEY_REQ_AUTH_PARAMS);

		/*
		 * Clean error messages. See:i.w.DiabloExtraController#errReads()
		 */
		unbind(KEY_ERR_SESSION_SAVED);
	}

	/**
	 * Make logged-in response message.
	 * 
	 * @param request
	 *            Servlet request
	 * @param fromAppName
	 *            from application name
	 * @param grantTicket
	 *            logged information model
	 * @param successUrl
	 *            login success redirect URL
	 * @return
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private RespBase<String> makeLoggedResponse(ServletRequest request, String grantTicket, String successUrl, Map params) {
		hasText(successUrl, "'successUrl' must not be empty");

		// Redirection URL
		StringBuffer successRedirectUrl = new StringBuffer(successUrl);
		if (isNotBlank(grantTicket)) {
			if (successRedirectUrl.lastIndexOf("?") > 0) {
				successRedirectUrl.append("&");
			} else {
				successRedirectUrl.append("?");
			}
			successRedirectUrl.append(config.getParam().getGrantTicket()).append("=").append(grantTicket);
		}

		// Relative path?
		String redirectUrl = successRedirectUrl.toString();
		if (startsWith(redirectUrl, "/")) {
			redirectUrl = getRFCBaseURI(toHttp(request), true) + successRedirectUrl;
		}

		// Make message:
		RespBase<String> resp = RespBase.create(SESSION_STATUS_AUTHC);
		resp.setCode(OK).setMessage("Authentication successful");
		params.put(config.getParam().getRedirectUrl(), redirectUrl);
		resp.getData().putAll(params);
		resp.getData().put(KEY_SERVICE_ROLE, KEY_SERVICE_ROLE_VALUE_IAMSERVER);
		return resp;
	}

	/**
	 * Make login failed response message.
	 * 
	 * @param failRedirectUrl
	 *            failure redirect URL
	 * @param request
	 *            Servlet request
	 * @param params
	 *            Redirected query configuration parameters
	 * @param thw
	 *            Exception object
	 * @return
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private String makeFailedResponse(String failRedirectUrl, ServletRequest request, Map params, String errmsg) {
		errmsg = (isNotBlank(errmsg)) ? errmsg : "Authentication failure";
		// Make message
		RespBase<String> resp = RespBase.create(SESSION_STATUS_UNAUTHC);
		resp.setCode(UNAUTHC).setMessage(errmsg);
		resp.getData().putAll(params);
		resp.getData().put(config.getParam().getRedirectUrl(), failRedirectUrl);
		resp.getData().put(KEY_SERVICE_ROLE, KEY_SERVICE_ROLE_VALUE_IAMSERVER);
		return toJSONString(resp);
	}

	/**
	 * Determine the URL of the login success redirection, default: successURL,
	 * can support customization.
	 * 
	 * @param token
	 * @param subject
	 * @param request
	 * @param response
	 * @param successUrl
	 * @return
	 */
	private String determineSuccessUrl(IamAuthenticationToken token, Subject subject, ServletRequest request,
			ServletResponse response, String successUrl) {
		// Call determine successUrl.
		successUrl = configurer.determineLoginSuccessUrl(successUrl, token, subject, request, response);
		hasText(successUrl, "Success redirectUrl is empty, please check the configure");
		return URI.create(successUrl).toString(); // Check symbol
	}

	/**
	 * Determine the URL of the login failure redirection, default: loginURL,
	 * can support customization.
	 * 
	 * @param token
	 * @param ae
	 * @param request
	 * @param response
	 * @return
	 */
	private String determineFailureUrl(IamAuthenticationToken token, AuthenticationException ae, ServletRequest request,
			ServletResponse response) {
		// Callback fail redirect URI
		String failRedirectUrl = getRedirectInfo(request, true).getRedirectUrl();

		// Fix Infinite redirection,AuthenticatorAuthenticationFilter may
		// redirect to loginUrl,if failRedirectUrl==getLoginUrl,it will happen
		// infinite redirection.
		if (this instanceof AuthenticatorAuthenticationFilter || isBlank(failRedirectUrl)) {
			failRedirectUrl = getLoginUrl();
		}

		String loginUrl = configurer.determineLoginFailureUrl(failRedirectUrl, token, ae, request, response);
		hasText(loginUrl, "'loginUrl' is empty, please check the configure");
		return cleanURI(loginUrl); // symbol check
	}

	/**
	 * Get filter name
	 */
	public abstract String getName();

}