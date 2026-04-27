package habittracker.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    // Redirect the user to the habits page
    @GetMapping("/")
    public String home() {
        return "redirect:/habits";
    }
}
