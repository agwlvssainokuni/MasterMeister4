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

package cherry.mastermeister.audit.entity;

/**
 * domain-entities.md §6.1。UNIT-02で追加する9種別。他ユニットが追加する種別は
 * 各ユニットのFunctional Designで定義し、本enumに追記する。
 * UNIT-03: CONNECTION_REGISTERED/CONNECTION_UPDATED/CONNECTION_DELETED/SCHEMA_IMPORTEDを追加
 * （unit-03/functional-design/domain-entities.md §3）。
 */
public enum AuditEventType {
    LOGIN,
    LOGOUT,
    LOGIN_FAILURE,
    REGISTRATION_REQUESTED,
    REGISTRATION_COMPLETED,
    USER_APPROVED,
    USER_REJECTED,
    USER_DISABLED,
    USER_ENABLED,
    TOKEN_REUSE_DETECTED,
    CONNECTION_REGISTERED,
    CONNECTION_UPDATED,
    CONNECTION_DELETED,
    SCHEMA_IMPORTED
}
