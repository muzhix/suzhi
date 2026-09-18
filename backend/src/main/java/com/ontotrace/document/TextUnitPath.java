package com.ontotrace.document;

/**
 * 目录聚合用的 path 与年附注，不含正文。
 *
 * @param path 结构路径
 * @param ceYear 公元纪年，没有则为 null
 * @param ganzhi 干支，没有则为 null
 * @author hanbd
 */
public record TextUnitPath(String path, Integer ceYear, String ganzhi) {}
