/**
 * Copyright (c) 2015 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.trustedanalytics.das.store.cloud;

import org.trustedanalytics.das.store.RequestStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.trustedanalytics.das.parser.Request;
import org.trustedanalytics.redis.encryption.EncryptionService;
import org.trustedanalytics.redis.encryption.serializer.SecureJacksonJsonRedisSerializer;

@Configuration
@Profile("cloud")
public class CloudStoreConfig {

    @Value("requests")
    private String redisRequestsKey;

    @Value("${request.store.db.cipher.key}")
    private String cipher;

    @Bean
    protected EncryptionService encryptionService() {
        return new EncryptionService(cipher);
    }

    @Bean
    SecureJacksonJsonRedisSerializer<Request> secureJacksonJsonRedisSerializer(EncryptionService encryptionService) {
        return new SecureJacksonJsonRedisSerializer<Request>(Request.class, encryptionService);
    }

    @Bean
    public RequestStore redisRequestStore(RedisOperations<String, Request> redisTemplate) {
        return new RedisRequestRepository(redisTemplate.boundHashOps(redisRequestsKey));
    }

    @Bean
    public RedisOperations<String, Request> redisTemplate(RedisConnectionFactory redisConnectionFactory,
                                                          RedisSerializer<Request> requestSerializer) {
        RedisTemplate<String, Request> template = new RedisTemplate<String, Request>();

        template.setConnectionFactory(redisConnectionFactory);

        RedisSerializer<String> keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setValueSerializer(requestSerializer);
        template.setHashKeySerializer(keySerializer);
        template.setHashValueSerializer(requestSerializer);

        return template;
    }
}