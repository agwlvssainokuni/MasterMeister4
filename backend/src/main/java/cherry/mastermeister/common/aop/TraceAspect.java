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

package cherry.mastermeister.common.aop;

import cherry.mastermeister.common.config.AppProperties;
import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.interceptor.CustomizableTraceInterceptor;
import org.springframework.stereotype.Component;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * reference/trace/TraceAspect.javaを本プロジェクトの構成（AppProperties経由の設定管理、
 * ベースパッケージ{@code cherry.mastermeister}）に合わせて移植したもの。
 * {@code CustomizableTraceInterceptor}に処理を委譲し、対象パッケージ配下の全メソッドの
 * 呼び出し・復帰・例外をログ出力する。実際にログが出力されるかは
 * {@code logging.level.cherry.mastermeister}（application.yml）の閾値に依存する
 * （TRACEレベル時のみ出力、それ以外は本アスペクトのプロキシ経由呼び出しのオーバーヘッドのみ）。
 * <p>
 * <b>注意（機微情報）</b>: {@code CustomizableTraceInterceptor}はメソッドの引数・戻り値を
 * そのまま{@code toString()}してログ出力するため、TRACE有効時は認証関連処理（ログインの
 * パスワード平文、発行したJWT/リフレッシュトークン等）がログに残る（SECURITY-03、
 * requirements.md §6.3に抵触しうる）。このためデフォルトではTRACEを無効化しており
 * （application.ymlの{@code logging.level.cherry.mastermeister}既定値はINFO）、
 * トラブルシューティング等で一時的に有効化する用途に限定する（本番環境で常時有効化しない）。
 */
@Component
@Aspect
public class TraceAspect {

    private final CustomizableTraceInterceptor traceInterceptor;

    public TraceAspect(AppProperties appProperties) {
        AppProperties.Trace trace = appProperties.trace();
        traceInterceptor = new CustomizableTraceInterceptor();
        traceInterceptor.setUseDynamicLogger(trace.useDynamicLogger());
        traceInterceptor.setHideProxyClassNames(trace.hideProxyClassNames());
        traceInterceptor.setLogExceptionStackTrace(trace.logExceptionStackTrace());
        traceInterceptor.setEnterMessage(trace.enterMessage());
        traceInterceptor.setExitMessage(trace.exitMessage());
        traceInterceptor.setExceptionMessage(trace.exceptionMessage());
    }

    // common.configは除外する: AppProperties（@ConfigurationPropertiesのBean）がJavaのrecord
    // （暗黙的にfinal）であり、対象に含めるとCGLIBによるプロキシ生成に失敗しアプリ起動自体が
    // 失敗する（実機起動確認で発見）。
    @Around("""
            execution(* cherry.mastermeister..*(..))
            && !within(cherry.mastermeister.common.config..*)
            """)
    public Object trace(ProceedingJoinPoint joinPoint) throws Throwable {
        return traceInterceptor.invoke(
                new ProceedingJoinPointMethodInvocation(joinPoint)
        );
    }

    static class ProceedingJoinPointMethodInvocation implements MethodInvocation {
        private final ProceedingJoinPoint joinPoint;

        ProceedingJoinPointMethodInvocation(ProceedingJoinPoint joinPoint) {
            this.joinPoint = joinPoint;
        }

        @Override
        public Object proceed() throws Throwable {
            return joinPoint.proceed();
        }

        @Override
        public Object[] getArguments() {
            return joinPoint.getArgs();
        }

        @Override
        public Object getThis() {
            return joinPoint.getThis();
        }

        @Override
        public Method getMethod() {
            return Optional.of(joinPoint).map(ProceedingJoinPoint::getSignature)
                    .filter(MethodSignature.class::isInstance).map(MethodSignature.class::cast)
                    .map(MethodSignature::getMethod)
                    .get();
        }

        @Override
        public AccessibleObject getStaticPart() {
            return getMethod();
        }
    }
}
