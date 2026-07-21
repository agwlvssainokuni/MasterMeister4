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

package cherry.mastermeister.rdbmsconnection.dialect;

import cherry.mastermeister.rdbmsconnection.entity.DbType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * component-methods.mdの"resolveDialect(dbType): RdbmsDialectStrategy ファクトリメソッド"を、
 * Spring管理下の4実装をDIで受け取れる専用コンポーネントとして実装したもの。
 */
@Component
public class RdbmsDialectStrategyResolver {

    private final Map<DbType, RdbmsDialectStrategy> strategiesByDbType;

    public RdbmsDialectStrategyResolver(List<RdbmsDialectStrategy> strategies) {
        this.strategiesByDbType = strategies.stream()
                .collect(Collectors.toMap(RdbmsDialectStrategy::dbType, Function.identity()));
    }

    public RdbmsDialectStrategy resolve(DbType dbType) {
        RdbmsDialectStrategy strategy = strategiesByDbType.get(dbType);
        if (strategy == null) {
            throw new IllegalStateException("No RdbmsDialectStrategy registered for dbType=" + dbType);
        }
        return strategy;
    }
}
