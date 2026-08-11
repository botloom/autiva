/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.bitloom.agentic.session;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public final class Session {

    private final String id;

    private final String userId;

    private final Instant createdAt;

    private final Map<String, Object> metadata;

    @JsonCreator
    private Session(
            @JsonProperty("id") String id,
            @JsonProperty("userId") String userId,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        this.id = id;
        this.userId = userId;
        this.createdAt = createdAt;
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Unique identifier for this session.
     */
    public String id() {
        return this.id;
    }

    /**
     * The actor (user or agent) who owns this session. Critical for isolation.
     */
    public String userId() {
        return this.userId;
    }

    /**
     * When this session was created.
     */
    public Instant createdAt() {
        return this.createdAt;
    }

    /**
     * Arbitrary metadata: model info, tags, agent type, etc.
     */
    public Map<String, Object> metadata() {
        return this.metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String id = "";

        private String userId = "";

        private Instant createdAt = Instant.now();

        private Map<String, Object> metadata = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = new HashMap<>(metadata);
            return this;
        }

        public Session build() {
            return new Session(this.id, this.userId, this.createdAt, this.metadata);
        }

    }

}
