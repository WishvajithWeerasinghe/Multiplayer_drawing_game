package com.example.draw.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppInfoController {

    @GetMapping("/api/app-info")
    public AppInfo appInfo() {
        return new AppInfo("collab-draw", 2);
    }

    public record AppInfo(String name, int uiVersion) {}
}
