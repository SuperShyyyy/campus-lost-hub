package com.hub.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * springdoc 将 UI 配在 {@code /api/doc.html}，根路径 {@code /doc.html} 无映射时易 404，此处做跳转便于记忆。
 */
@Controller
public class SwaggerDocRedirectController {

    @GetMapping("/doc.html")
    public String redirectToApiDoc() {
        return "redirect:/api/doc.html";
    }
}
