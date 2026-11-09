package io.github.jkanie.schedkeel.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves index.html for the dashboard's client-side routes, so a reload on
 * /jobs/nightly-report still opens the dashboard.
 */
@Controller
class DashboardRoutes {

    @GetMapping({"/login", "/jobs", "/jobs/{name}", "/runs"})
    String dashboard() {
        return "forward:/index.html";
    }
}
