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

package cherry.mastermeister.registration;

import cherry.mastermeister.common.mail.MailDeliveryService;
import cherry.mastermeister.registration.entity.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * COMP-06。business-logic-model.md §9、BR-MAIL-01〜03。送信失敗時はフェイルオープン
 * （nfr-design-patterns.md §1.2）とし、呼び出し元の業務処理をロールバックさせない
 * （テンプレートレンダリング・SMTP送信の実処理は{@link MailDeliveryService}に委譲する）。
 */
@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final MailDeliveryService mailDeliveryService;

    public EmailNotificationService(MailDeliveryService mailDeliveryService) {
        this.mailDeliveryService = mailDeliveryService;
    }

    /**
     * Step1（登録確認メール）。Userレコード未作成のため、リクエスト時点で選択されていたUI言語を用いる（BR-MAIL-01）。
     */
    public void sendRegistrationConfirmation(String email, String confirmationUrl, Language language) {
        send(email, "registration-confirmation", language, Map.of("confirmationUrl", confirmationUrl));
    }

    /**
     * 承認結果通知。Userの保存済み{@code preferredLanguage}を用いる（BR-MAIL-01）。
     */
    public void sendApprovalResult(String email, String fullName, String loginUrl, Language language) {
        send(email, "approval-result", language, Map.of("fullName", fullName, "loginUrl", loginUrl));
    }

    /**
     * 却下結果通知。Userの保存済み{@code preferredLanguage}を用いる（BR-MAIL-01）。
     */
    public void sendRejectionResult(String email, String fullName, Language language) {
        send(email, "rejection-result", language, Map.of("fullName", fullName));
    }

    private void send(String to, String templateName, Language language, Map<String, Object> variables) {
        try {
            mailDeliveryService.send(to, templateName, language.name(), variables);
        } catch (Exception e) {
            log.warn("Failed to send email (template={}, fail-open per nfr-design-patterns.md §1.2): {}",
                    templateName, e.toString());
        }
    }
}
