package com.kailas.settlementengine.scheduler;

import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    @Bean
    public JobDetail settlementJobDetail() {
        return JobBuilder.newJob(SettlementJob.class)
                .withIdentity("settlementJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger settlementTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(settlementJobDetail())
                .withIdentity("settlementTrigger")
                .withSchedule(
                        SimpleScheduleBuilder.simpleSchedule()
                                .withIntervalInSeconds(30)
                                .repeatForever()
                )
                .build();
    }
}
