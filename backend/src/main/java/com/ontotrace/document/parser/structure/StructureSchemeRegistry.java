package com.ontotrace.document.parser.structure;

import com.ontotrace.web.ApiExceptionHandler.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 加载 classpath 预置结构方案。文件名与展示名按体例，不出现书名。
 *
 * @author hanbd
 */
@Slf4j
@Component
public class StructureSchemeRegistry {

    private static final List<String> PRESET_FILES = List.of(
            "jizhuan-toc-divergent.json", "jizhuan-toc-same.json", "biannian-juan-ji-nian.json");

    private final Map<String, StructureProfile> presets = new LinkedHashMap<>();

    /**
     * 读取预置方案。
     */
    public StructureSchemeRegistry() {
        for (String file : PRESET_FILES) {
            String path = "/structure-schemes/" + file;
            try (InputStream in = StructureSchemeRegistry.class.getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException("缺少预置结构方案 " + file);
                }
                StructureProfile profile = StructureJson.read(in.readAllBytes(), StructureProfile.class);
                presets.put(profile.id(), profile);
                log.info("loaded structure scheme id={} name={}", profile.id(), profile.name());
            } catch (IOException ex) {
                throw new IllegalStateException("无法读取预置结构方案 " + file, ex);
            }
        }
    }

    /**
     * 预置列表，顺序固定。
     *
     * @return 方案
     */
    public List<StructureProfile> list() {
        return new ArrayList<>(presets.values());
    }

    /**
     * 按标识读取预置。
     *
     * @param id 方案标识
     * @return 方案
     */
    public StructureProfile require(String id) {
        StructureProfile profile = presets.get(id);
        if (profile == null) {
            throw new NotFoundException("结构方案不存在");
        }
        return profile;
    }

    /**
     * 请求体中的覆盖优先；否则用预置。两者都空则表示无方案。
     *
     * @param schemeId 预置标识
     * @param override 覆盖体
     * @return 方案，无方案时为空
     */
    public StructureProfile resolve(String schemeId, StructureProfile override) {
        if (override != null && override.headings() != null && !override.headings().isEmpty()) {
            String id = override.id() != null ? override.id() : schemeId;
            String name = override.name() != null ? override.name() : id;
            return new StructureProfile(
                    id,
                    name,
                    override.toc(),
                    override.headings(),
                    override.alignment(),
                    override.paragraph(),
                    override.neighbor(),
                    override.skipEduNodeTypes());
        }
        if (schemeId == null || schemeId.isBlank()) {
            return null;
        }
        return require(schemeId);
    }
}
