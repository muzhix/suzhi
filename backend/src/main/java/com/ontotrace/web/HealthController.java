package com.ontotrace.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用存活检查。
 *
 * @author hanbd
 */
@RestController
public class HealthController {

    /**
     * 返回存活状态。
     *
     * @return 状态映射
     */
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
