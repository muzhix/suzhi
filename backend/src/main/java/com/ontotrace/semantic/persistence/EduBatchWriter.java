package com.ontotrace.semantic.persistence;

import com.ontotrace.semantic.Edu;
import com.ontotrace.semantic.EduArgument;
import com.ontotrace.semantic.EduSourceRef;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * EDU 短事务批量写入。模型调用发生在事务外，本类只负责单条 EDU 的原子落库与重跑前清理，
 * 避免整篇抽取长期持有数据库事务。重跑为覆盖式重写：物理删除范围内旧 EDU 后重写，
 * 暂不引入 superseded 状态迁移（见 README 已知偏差）。
 *
 * @author hanbd
 */
@Slf4j
@Component
public class EduBatchWriter {

    private final EduRepository eduRepo;
    private final EduSourceRefRepository eduSourceRefRepo;
    private final EduArgumentRepository eduArgumentRepo;

    /**
     * 创建写入器。
     *
     * @param eduRepo EDU 仓储
     * @param eduSourceRefRepo 来源仓储
     * @param eduArgumentRepo 参数仓储
     */
    public EduBatchWriter(
            EduRepository eduRepo, EduSourceRefRepository eduSourceRefRepo, EduArgumentRepository eduArgumentRepo) {
        this.eduRepo = eduRepo;
        this.eduSourceRefRepo = eduSourceRefRepo;
        this.eduArgumentRepo = eduArgumentRepo;
    }

    /**
     * 单条 EDU 及其来源引用、参数在一个短事务中写入。崩溃时已写入条目保留，
     * 未完成部分由任务重试时的清理删除。
     *
     * @param edu EDU
     * @param sourceRefs 来源引用
     * @param eduArguments 参数
     */
    @Transactional
    public void write(Edu edu, List<EduSourceRef> sourceRefs, List<EduArgument> eduArguments) {
        eduRepo.save(edu);
        eduSourceRefRepo.saveAll(sourceRefs);
        eduArgumentRepo.saveAll(eduArguments);
    }

    /**
     * 重跑前删除范围内旧 EDU 及其来源、参数（含上次尝试的半成品）。
     * 范围可以是整个版本、引用某一文本单元的 EDU，或引用一组 path 前缀单元的 EDU。
     *
     * @param documentVersionId 固定文档版本
     * @param textUnitId 局部重跑的文本单元，空表示未按单段
     * @param textUnitIds path 前缀下的文本单元，空表示未按前缀
     * @return 删除的 EDU 条数
     */
    @Transactional
    public int deleteExisting(UUID documentVersionId, UUID textUnitId, List<UUID> textUnitIds) {
        if (textUnitId != null) {
            return deleteByEduIds(documentVersionId, eduSourceRefRepo.findEduIdsByTextUnitId(textUnitId), textUnitId);
        }
        if (textUnitIds != null) {
            if (textUnitIds.isEmpty()) {
                return 0;
            }
            return deleteByEduIds(documentVersionId, eduSourceRefRepo.findEduIdsByTextUnitIdIn(textUnitIds), null);
        }
        eduArgumentRepo.deleteByDocumentVersionId(documentVersionId);
        eduSourceRefRepo.deleteByDocumentVersionId(documentVersionId);
        long count = eduRepo.countByDocumentVersionId(documentVersionId);
        eduRepo.deleteByDocumentVersionId(documentVersionId);
        if (count > 0) {
            log.info("deleted edu versionId={} count={}", documentVersionId, count);
        }
        return (int) count;
    }

    /**
     * 重跑前删除范围内旧 EDU。范围可以是整个版本，或引用某一文本单元的 EDU。
     *
     * @param documentVersionId 固定文档版本
     * @param textUnitId 局部重跑的文本单元，空表示全文
     * @return 删除的 EDU 条数
     */
    @Transactional
    public int deleteExisting(UUID documentVersionId, UUID textUnitId) {
        return deleteExisting(documentVersionId, textUnitId, null);
    }

    private int deleteByEduIds(UUID documentVersionId, List<UUID> eduIds, UUID textUnitId) {
        if (eduIds == null || eduIds.isEmpty()) {
            return 0;
        }
        eduArgumentRepo.deleteByEduIdIn(eduIds);
        eduSourceRefRepo.deleteByEduIdIn(eduIds);
        eduRepo.deleteByIdIn(eduIds);
        log.info("deleted edu versionId={} textUnitId={} count={}", documentVersionId, textUnitId, eduIds.size());
        return eduIds.size();
    }
}
