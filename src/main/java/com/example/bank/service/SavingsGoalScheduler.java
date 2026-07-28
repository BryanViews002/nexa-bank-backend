package com.example.bank.service;

import com.example.bank.entity.SavingsGoal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SavingsGoalScheduler {

    private static final Logger log = LoggerFactory.getLogger(SavingsGoalScheduler.class);

    private final SavingsGoalService savingsGoalService;

    public SavingsGoalScheduler(SavingsGoalService savingsGoalService) {
        this.savingsGoalService = savingsGoalService;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.savings-goal-delay-ms}")
    public void runDueAutoContributions() {
        for (SavingsGoal goal : savingsGoalService.findDueAutoContributions()) {
            try {
                savingsGoalService.runAutoContribution(goal.getId());
            } catch (RuntimeException exception) {
                log.warn("Auto-contribution for savings goal {} failed", goal.getId(), exception);
            }
        }
    }
}
