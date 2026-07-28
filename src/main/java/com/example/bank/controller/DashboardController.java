package com.example.bank.controller;

import com.example.bank.dto.DashboardResponse;
import com.example.bank.entity.User;
import com.example.bank.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ResponseEntity<DashboardResponse> get(Authentication authentication) {
        return ResponseEntity.ok(dashboardService.getDashboard((User) authentication.getPrincipal()));
    }
}
