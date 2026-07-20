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

import cherry.mustache.Mustache;
import cherry.mustache.Template;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * business-rules.md BR-MAIL-02〜03。{@code cherry-mustache-core}をラップし、言語別HTMLテンプレートの
 * 解決・レンダリング・件名（{@code <title>}要素）抽出を行う。
 */
@Component
public class MailTemplateRenderer {

    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE);

    /**
     * @param templateName テンプレート名（拡張子を除くファイル名。例: "registration-confirmation"）
     * @param language     言語コード（"ja"/"en"）。{@code mail-templates/{language}/{templateName}.html}を解決する
     * @param variables    テンプレートに埋め込む変数
     * @return 件名・本文（HTML）
     */
    public RenderedMail render(String templateName, String language, Map<String, Object> variables) {
        String resourcePath = "mail-templates/%s/%s.html".formatted(language, templateName);
        String templateSource = loadTemplate(resourcePath);
        Template template = Mustache.compile(templateSource);
        String renderedHtml = template.render(variables);
        String subject = extractSubject(renderedHtml, resourcePath);
        return new RenderedMail(subject, renderedHtml);
    }

    private String loadTemplate(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Mail template not found: " + resourcePath, e);
        }
    }

    /**
     * BR-MAIL-03の抽出手順:
     * 1. レンダリング後の文字列に対して行う（呼び出し元で既に変数展開済み）
     * 2. 抽出専用のコピーから改行文字を除去する（実際の送信本文には影響させない）
     * 3. {@code <title>}〜{@code </title>}を非貪欲マッチで抽出する
     * 4. トリム・HTMLエンティティデコードを行う
     * 5. 空、または{@code <title>}が見つからない場合は例外を送出する（フェイルファスト）
     */
    private String extractSubject(String renderedHtml, String resourcePath) {
        String forExtraction = renderedHtml.replaceAll("\\R", "");
        Matcher matcher = TITLE_PATTERN.matcher(forExtraction);
        if (!matcher.find()) {
            throw new IllegalStateException(
                    "Mail template is missing a <title> element for the subject: " + resourcePath);
        }
        String subject = HtmlUtils.htmlUnescape(matcher.group(1).trim());
        if (subject.isEmpty()) {
            throw new IllegalStateException("Mail template <title> must not be empty: " + resourcePath);
        }
        return subject;
    }
}
