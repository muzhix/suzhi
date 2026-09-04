package com.ontotrace.document.parser;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * TXT/Markdown 同步导入器。
 *
 * @author hanbd
 */
@Component
public class TextDocumentParser implements DocumentParser {

    /**
     * 提交后立即完成。
     *
     * @param request 解析请求
     * @return 可立即取得结果的句柄
     */
    @Override
    public ParseHandle submit(ParseRequest request) {
        return new ParseHandle("text:" + UUID.randomUUID());
    }

    /**
     * 返回标准化文本。
     *
     * @param handle 句柄
     * @return 成功结果
     */
    @Override
    public ParseResult poll(ParseHandle handle) {
        throw new UnsupportedOperationException("使用 parse(bytes) 同步导入文本");
    }

    /**
     * 把字节转为标准化展示文本。
     *
     * @param request 解析请求
     * @return 解析结果
     */
    public ParseResult parse(ParseRequest request) {
        String text = new String(request.bytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (text.isBlank()) {
            return new ParseResult("failed", null, "文本为空");
        }
        return new ParseResult("succeeded", text, null);
    }
}
