package com.ontotrace.semantic;

import com.ontotrace.security.CurrentUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * EDU 读取接口。
 *
 * @author hanbd
 */
@RestController
@RequestMapping("/api")
public class EduController {

    private final EduService eduService;

    /**
     * 创建控制器。
     *
     * @param eduService EDU 服务
     */
    public EduController(EduService eduService) {
        this.eduService = eduService;
    }

    /**
     * 读取固定版本 EDU。
     *
     * @param user 当前用户
     * @param versionId 版本标识
     * @return EDU 列表
     */
    @GetMapping("/document-versions/{versionId}/edus")
    public List<EduResponse> list(@AuthenticationPrincipal CurrentUser user, @PathVariable java.util.UUID versionId) {
        return eduService.list(user, versionId).stream().map(EduResponse::from).toList();
    }

    /**
     * EDU 响应。
     *
     * @param id 标识
     * @param type 类型
     * @param text 自足表述
     * @param predicate 谓词
     * @param status 状态
     * @param locationPrecision 定位精度
     * @param reviewResult 复核结果
     * @param sources 来源
     * @param arguments 参数
     */
    public record EduResponse(
            String id,
            String type,
            String text,
            String predicate,
            String status,
            String locationPrecision,
            String reviewResult,
            List<SourceResponse> sources,
            List<ArgumentResponse> arguments) {
        static EduResponse from(EduService.EduDetail detail) {
            Edu edu = detail.edu();
            return new EduResponse(
                    edu.getId().toString(),
                    edu.getType(),
                    edu.getText(),
                    edu.getPredicate(),
                    edu.getStatus(),
                    edu.getLocationPrecision(),
                    edu.getReviewResult(),
                    detail.sources().stream()
                            .map(source -> new SourceResponse(
                                    source.getId().toString(),
                                    source.getTextUnitId().toString(),
                                    source.getQuote(),
                                    source.getCharStart(),
                                    source.getCharEnd(),
                                    source.getPrecision(),
                                    source.getPurpose()))
                            .toList(),
                    detail.arguments().stream()
                            .map(argument -> new ArgumentResponse(
                                    argument.getRole(), argument.getRoleName(), argument.getValue(), argument.getSourceForm()))
                            .toList());
        }
    }

    /**
     * 来源响应。
     *
     * @param id 标识
     * @param textUnitId 文本单元
     * @param quote 摘录
     * @param charStart 起始
     * @param charEnd 结束
     * @param precision 精度
     * @param purpose 用途
     */
    public record SourceResponse(
            String id,
            String textUnitId,
            String quote,
            Integer charStart,
            Integer charEnd,
            String precision,
            String purpose) {}

    /**
     * 参数响应。
     *
     * @param role 角色
     * @param roleName 角色名
     * @param value 规范值
     * @param sourceForm 原文写法
     */
    public record ArgumentResponse(String role, String roleName, String value, String sourceForm) {}
}
