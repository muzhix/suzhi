package com.ontotrace.runcontrol;

/**
 * 一类任务的执行器。
 *
 * @author hanbd
 */
public interface JobHandler {

    /**
     * 任务类型。
     *
     * @return 类型编码
     */
    String type();

    /**
     * 执行任务。必须能识别已完成步骤，避免重试重复写。
     *
     * @param job 任务
     */
    void execute(Job job);
}
