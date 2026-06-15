package com.example.gateway.service;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
public class RateLimiterService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<Long> tokenBucketScript;

    public RateLimiterService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        
        String script = """
                local key = KEYS[1]
                local capacity = tonumber(ARGV[1])
                local now = tonumber(ARGV[2])
                local fill_time = 60
                
                local last_tokens = tonumber(redis.call('HGET', key, 'tokens'))
                if last_tokens == nil then
                  last_tokens = capacity
                end
                
                local last_refreshed = tonumber(redis.call('HGET', key, 'timestamp'))
                if last_refreshed == nil then
                  last_refreshed = 0
                end
                
                local delta = math.max(0, now - last_refreshed)
                local filled_tokens = math.min(capacity, last_tokens + (delta * (capacity / fill_time)))
                
                local allowed = filled_tokens >= 1
                local new_tokens = filled_tokens
                if allowed then
                  new_tokens = filled_tokens - 1
                end
                
                redis.call('HSET', key, 'tokens', new_tokens)
                redis.call('HSET', key, 'timestamp', now)
                redis.call('EXPIRE', key, fill_time)
                
                if allowed then
                  return 1
                else
                  return 0
                end
                """;
        this.tokenBucketScript = RedisScript.of(script, Long.class);
    }

    public Mono<Boolean> isAllowed(String apiKey, int maxRequestsPerMinute) {
        String key = "rate_limit:" + apiKey;
        long now = Instant.now().getEpochSecond();
        
        return redisTemplate.execute(
                tokenBucketScript,
                List.of(key),
                List.of(String.valueOf(maxRequestsPerMinute), String.valueOf(now))
        ).next().map(result -> result == 1L);
    }
}
