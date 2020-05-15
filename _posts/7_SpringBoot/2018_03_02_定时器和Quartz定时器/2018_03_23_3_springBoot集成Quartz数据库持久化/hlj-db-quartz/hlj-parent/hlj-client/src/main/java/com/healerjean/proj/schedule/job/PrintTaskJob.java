package com.healerjean.proj.schedule.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/**
 * @author HealerJean
 * @ClassName PrintTaskJob
 * @date 2020/5/15  17:27.
 * @Description
 */
@Slf4j
@Component
public class PrintTaskJob implements Job {

    @Override
    public void execute(JobExecutionContext context) {
        try {
            CronTrigger trigger = (CronTrigger) context.getTrigger();
            String corn = trigger.getCronExpression();
            String jobName = trigger.getKey().getName();
            String jobGroup = trigger.getKey().getGroup();
            log.info("定时器任务开始执行--------【PrintTaskJob】 ,任务corn：{}, 任务名称：{}， 任务组：{}", corn, jobName, jobGroup);
        } catch (Exception e) {
            log.error("定时器任务--------【PrintTaskJob】 任务执行失败");
        }
    }
}
