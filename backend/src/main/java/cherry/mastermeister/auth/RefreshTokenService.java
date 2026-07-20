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

package cherry.mastermeister.auth;

import cherry.mastermeister.audit.AuditEventPublisher;
import cherry.mastermeister.audit.entity.AuditEventType;
import cherry.mastermeister.audit.entity.ResultStatus;
import cherry.mastermeister.audit.event.AuditEvent;
import cherry.mastermeister.auth.entity.RefreshToken;
import cherry.mastermeister.auth.entity.RevokeReason;
import cherry.mastermeister.auth.repository.RefreshTokenRepository;
import cherry.mastermeister.common.config.AppProperties;
import cherry.mastermeister.common.exception.RefreshTokenInvalidException;
import cherry.mastermeister.common.security.TokenGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * COMP-04。business-logic-model.md §6、BR-TOKEN-01〜04。
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenGenerator tokenGenerator;
    private final AuditEventPublisher auditEventPublisher;
    private final AppProperties.Jwt jwtProperties;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, TokenGenerator tokenGenerator,
                                AuditEventPublisher auditEventPublisher, AppProperties appProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenGenerator = tokenGenerator;
        this.auditEventPublisher = auditEventPublisher;
        this.jwtProperties = appProperties.jwt();
    }

    /**
     * ログイン成功時、新規のトークンファミリを発行する。
     */
    @Transactional
    public IssuedRefreshToken issue(Long userId) {
        return issueInFamily(userId, UUID.randomUUID().toString());
    }

    /**
     * @param rawToken 提示されたリフレッシュトークン（平文）
     * @return ローテーション後の新しいリフレッシュトークン
     * @throws RefreshTokenInvalidException トークンが存在しない・期限切れの場合。再利用検知時も同様に送出する
     */
    @Transactional
    public IssuedRefreshToken rotate(String rawToken) {
        String hash = tokenGenerator.hash(rawToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(RefreshTokenInvalidException::new);

        if (existing.isRevoked()) {
            handleReuseDetected(existing);
            throw new RefreshTokenInvalidException();
        }
        if (existing.isExpired(Instant.now())) {
            throw new RefreshTokenInvalidException();
        }

        existing.revoke(RevokeReason.ROTATED, Instant.now());
        refreshTokenRepository.save(existing);

        return issueInFamily(existing.getUserId(), existing.getTokenFamilyId());
    }

    /**
     * ログアウト時。対象が見つからなくても冪等に扱う。
     *
     * @return 失効させたトークンのuserId。対象が見つからない場合はnull
     */
    @Transactional
    public Long revokeByRawToken(String rawToken) {
        String hash = tokenGenerator.hash(rawToken);
        return refreshTokenRepository.findByTokenHash(hash)
                .map(token -> {
                    token.revoke(RevokeReason.LOGOUT, Instant.now());
                    refreshTokenRepository.save(token);
                    return token.getUserId();
                })
                .orElse(null);
    }

    /**
     * BR-TOKEN-04。管理者によるアカウント無効化時、当該ユーザに紐づく有効な全リフレッシュトークン
     * （複数端末・複数トークンファミリを含む）を失効させる。
     */
    @Transactional
    public void revokeAllForUser(Long userId) {
        Instant now = Instant.now();
        List<RefreshToken> active = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId);
        active.forEach(token -> token.revoke(RevokeReason.ADMIN_DISABLED, now));
        refreshTokenRepository.saveAll(active);
    }

    private void handleReuseDetected(RefreshToken reusedToken) {
        Instant now = Instant.now();
        List<RefreshToken> family = refreshTokenRepository.findAllByTokenFamilyId(reusedToken.getTokenFamilyId());
        family.stream()
                .filter(token -> !token.isRevoked())
                .forEach(token -> token.revoke(RevokeReason.REUSE_DETECTED, now));
        refreshTokenRepository.saveAll(family);

        auditEventPublisher.publish(new AuditEvent(now, reusedToken.getUserId(), null,
                AuditEventType.TOKEN_REUSE_DETECTED, reusedToken.getTokenFamilyId(), ResultStatus.FAILURE, null));
    }

    private IssuedRefreshToken issueInFamily(Long userId, String tokenFamilyId) {
        String rawToken = tokenGenerator.generate();
        String hash = tokenGenerator.hash(rawToken);
        Instant now = Instant.now();
        RefreshToken entity = new RefreshToken(userId, tokenFamilyId, hash, now,
                now.plus(jwtProperties.refreshTokenExpiry()));
        refreshTokenRepository.save(entity);
        return new IssuedRefreshToken(userId, rawToken);
    }
}
