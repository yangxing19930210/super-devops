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
package com.wl4g.devops.support.notification.mail;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.Assert;

import static com.wl4g.devops.support.config.NotificationAutoConfiguration.*;

/**
 * Email sender composite adapter template.
 * 
 * @author Wangl.sir
 * @version v1.0.0 2019-09-17
 * @since
 */
public class MailSenderTemplate {
	final protected Logger log = LoggerFactory.getLogger(getClass());

	/**
	 * Java mail sender.
	 */
	final protected JavaMailSender mailSender;

	@Value("${" + KEY_SPRING_MAIL_USER + ":none}")
	protected String fromUser;

	public MailSenderTemplate(JavaMailSender mailSender) {
		this.mailSender = mailSender;
		Assert.notNull(mailSender, "Mail sender must not be null.");
	}

	/**
	 * Send simple mail messages.
	 * 
	 * @param simpleMessages
	 */
	public void send(SimpleMailMessage... simpleMessages) {
		StringBuffer msgs = new StringBuffer();
		try {
			// Preset from account, otherwise it would be wrong: 501 mail from
			// address must be same as authorization user.
			for (SimpleMailMessage msg : simpleMessages) {
				msgs.append(msg.getText());
				msgs.append(",");
				msg.setFrom(msg.getFrom() + "<" + fromUser + ">"); // 要加“<>”这种格式才能发出去
			}
			mailSender.send(simpleMessages);
		} catch (Exception e) {
			log.error("Mail发送异常. request:  {}", ExceptionUtils.getRootCauseMessage(e));
		}
	}

}