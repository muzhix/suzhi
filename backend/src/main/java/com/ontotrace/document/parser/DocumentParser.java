package com.ontotrace.document.parser;

/**
 * 文档解析器。TXT/Markdown 与后续 MinerU 共用该契约。
 *
 * @author hanbd
 */
public interface DocumentParser {

    /**
     * 提交解析。
     *
     * @param request 解析请求
     * @return 任务句柄
     */
    ParseHandle submit(ParseRequest request);

    /**
     * 轮询解析结果。
     *
     * @param handle 任务句柄
     * @return 解析结果
     */
    ParseResult poll(ParseHandle handle);

    /**
     * 解析请求。
     *
     * @param objectKey 对象键
     * @param filename 文件名
     * @param mediaType 媒体类型
     * @param bytes 已读取的内容，同步解析器可直接使用
     */
    record ParseRequest(String objectKey, String filename, String mediaType, byte[] bytes) {}

    /**
     * 解析句柄。
     *
     * @param id 句柄标识
     */
    record ParseHandle(String id) {}

    /**
     * 解析结果。
     *
     * @param status waiting、running、succeeded 或 failed
     * @param text 标准化全文
     * @param error 失败摘要
     */
    record ParseResult(String status, String text, String error) {
        /**
         * 是否已结束。
         *
         * @return 成功或失败返回 true
         */
        public boolean finished() {
            return "succeeded".equals(status) || "failed".equals(status);
        }
    }
}
