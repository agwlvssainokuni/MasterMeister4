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

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BR-MAIL-03の件名抽出ロジックの境界値を検証する。
 */
class MailTemplateRendererTest {

    private final MailTemplateRenderer renderer = new MailTemplateRenderer();

    @Test
    void render_extractsSubjectAndVariablesFromRegistrationConfirmationTemplate_ja() {
        RenderedMail mail = renderer.render("registration-confirmation", "ja",
                Map.of("confirmationUrl", "https://example.com/register/complete?token=abc"));

        assertThat(mail.subject()).isEqualTo("MasterMeister - ユーザ登録の確認");
        assertThat(mail.htmlBody()).contains("https://example.com/register/complete?token=abc");
    }

    @Test
    void render_extractsSubject_en() {
        RenderedMail mail = renderer.render("registration-confirmation", "en",
                Map.of("confirmationUrl", "https://example.com/register/complete?token=abc"));

        assertThat(mail.subject()).isEqualTo("MasterMeister - Confirm Your Registration");
    }

    @Test
    void render_approvalResult_includesFullNameAndLoginUrl() {
        RenderedMail mail = renderer.render("approval-result", "ja",
                Map.of("fullName", "Taro Yamada", "loginUrl", "https://example.com/login"));

        assertThat(mail.subject()).isEqualTo("MasterMeister - アカウント承認のお知らせ");
        assertThat(mail.htmlBody()).contains("Taro Yamada").contains("https://example.com/login");
    }

    @Test
    void render_throws_whenTemplateFileDoesNotExist() {
        assertThatThrownBy(() -> renderer.render("nonexistent-template", "ja", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mail-templates/ja/nonexistent-template.html");
    }

    @Test
    void render_stripsNewlinesInTitle_withoutAffectingBody() {
        RenderedMail mail = renderer.render("edge-case-multiline-title", "ja", Map.of());

        assertThat(mail.subject()).isEqualTo("改行を含む件名");
        // 改行除去は件名抽出専用のコピーに対してのみ行い、本文には影響させない（BR-MAIL-03）
        assertThat(mail.htmlBody()).contains("\n");
    }

    @Test
    void render_extractsTitle_withAttributes() {
        RenderedMail mail = renderer.render("edge-case-title-with-attribute", "ja", Map.of());

        assertThat(mail.subject()).isEqualTo("属性付きtitle");
    }

    @Test
    void render_throws_whenTitleElementIsMissing() {
        assertThatThrownBy(() -> renderer.render("edge-case-missing-title", "ja", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("<title>");
    }

    @Test
    void render_throws_whenTitleIsEmpty() {
        assertThatThrownBy(() -> renderer.render("edge-case-empty-title", "ja", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("<title>");
    }

    @Test
    void render_decodesHtmlEntities_whenTitleContainsEscapedVariable() {
        // cherry-mustache-coreはMustache標準どおり変数を自動HTMLエスケープするため、
        // "A&B" は "A&amp;B" としてレンダリングされる。抽出時にデコードして元の文字列に戻す。
        RenderedMail mail = renderer.render("edge-case-html-entity", "ja", Map.of("subjectPart", "A&B"));

        assertThat(mail.subject()).isEqualTo("A&B のお知らせ");
    }
}
