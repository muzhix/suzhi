package com.ontotrace.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * LIKE 关键字转义。
 *
 * @author hanbd
 */
class LikeQueryTest {

    /**
     * 空白关键字表示不筛选；通配符被转义。
     */
    @Test
    void containsEscapesWildcards() {
        assertEquals("", LikeQuery.contains("  "));
        assertEquals("玄武门", LikeQuery.contains("玄武门"));
        assertEquals("100\\%", LikeQuery.contains("100%"));
        assertEquals("a\\_b", LikeQuery.contains("a_b"));
    }
}
