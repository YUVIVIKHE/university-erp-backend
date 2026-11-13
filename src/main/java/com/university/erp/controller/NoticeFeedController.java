package com.university.erp.controller;

import com.university.erp.entity.User;
import com.university.erp.service.admin.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only notice feed for the student / faculty portals (shows notices addressed to ALL or to the reader's role). */
@RestController
@RequiredArgsConstructor
public class NoticeFeedController {

    private final NoticeService notices;

    @GetMapping("/api/student/notices")
    public List<NoticeService.View> feed(@AuthenticationPrincipal User reader) {
        return notices.visibleFor(reader.getRole());
    }
}
