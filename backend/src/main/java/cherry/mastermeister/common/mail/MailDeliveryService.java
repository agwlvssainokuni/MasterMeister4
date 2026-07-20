/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cherry.mastermeister.common.mail;

import cherry.mastermeister.common.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * テンプレートのレンダリングとSMTP送信を行う横断的インフラ。送信失敗時の扱い（フェイルオープン等）は
 * 呼び出し元の業務ドメインごとの方針に委ねるため、ここでは例外を送出するのみとする。
 */
@Component
public class MailDeliveryService {

    private final MailTemplateRenderer mailTemplateRenderer;
    private final JavaMailSender mailSender;
    private final String from;

    public MailDeliveryService(MailTemplateRenderer mailTemplateRenderer, JavaMailSender mailSender,
                                AppProperties appProperties) {
        this.mailTemplateRenderer = mailTemplateRenderer;
        this.mailSender = mailSender;
        this.from = appProperties.mail().from();
    }

    /**
     * @param language 言語コード（"ja"/"en"）。{@link MailTemplateRenderer#render}参照
     * @throws MessagingException   MimeMessageの構築・送信に失敗した場合
     * @throws IllegalStateException テンプレートが見つからない、または{@code <title>}（件名）が
     *                                不正な場合（{@link MailTemplateRenderer#render}参照）
     */
    public void send(String to, String templateName, String language, Map<String, Object> variables)
            throws MessagingException, IllegalStateException {
        RenderedMail mail = mailTemplateRenderer.render(templateName, language, variables);
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(mail.subject());
        helper.setText(mail.htmlBody(), true);
        mailSender.send(message);
    }
}