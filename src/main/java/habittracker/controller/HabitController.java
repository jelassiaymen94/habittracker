package habittracker.controller;

import habittracker.model.Habit;
import habittracker.repository.HabitRepository;
import habittracker.service.HabitService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Controller
@RequestMapping("/habits")
public class HabitController {

    private final HabitRepository habitRepository;
    private final HabitService habitService;

    // Constructor injection (Spring's preferred way)
    @Autowired
    public HabitController(HabitRepository habitRepository, HabitService habitService) {
        this.habitRepository = habitRepository;
        this.habitService = habitService;
    }

    // GET /habits -> show the list page
    @GetMapping
    public String list(Model model) {
        List<Habit> habits = habitRepository.findAll();

        // Build a map: habitId -> "done today?"
        Map<Long, Boolean> doneToday = new HashMap<>();
        Map<Long, Long> totals = new HashMap<>();
        Map<Long, Integer> currentStreaks = new HashMap<>();
        Map<Long, Integer> longestStreaks = new HashMap<>();
        for (Habit h : habits) {
            doneToday.put(h.getId(), habitService.isDoneToday(h));
            totals.put(h.getId(), habitService.totalCompletions(h));
            currentStreaks.put(h.getId(), habitService.currentStreak(h));
            longestStreaks.put(h.getId(), habitService.longestStreak(h));
        }

        model.addAttribute("habits", habits);
        model.addAttribute("doneToday", doneToday);
        model.addAttribute("totals", totals);
        model.addAttribute("currentStreaks", currentStreaks);
        model.addAttribute("longestStreaks", longestStreaks);
        return "habits/list";
    }

    // GET /habits/new -> show the empty form
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("habit", new Habit());
        return "habits/form";
    }

    // GET /habits/{id} -> habit detail page with calendar
    @GetMapping("/{id}")
    public String show(@PathVariable Long id, Model model) {
        Habit habit = habitRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Habit not found: " + id));

        model.addAttribute("habit", habit);
        model.addAttribute("currentStreak", habitService.currentStreak(habit));
        model.addAttribute("longestStreak", habitService.longestStreak(habit));
        model.addAttribute("totalCompletions", habitService.totalCompletions(habit));
        model.addAttribute("doneToday", habitService.isDoneToday(habit));
        model.addAttribute("days", habitService.last90Days(habit));
        model.addAttribute("today", LocalDate.now());
        return "habits/show";
    }

    // POST /habits -> receive form data, save, redirect
    @PostMapping
    public String create(@Valid @ModelAttribute("habit") Habit habit, BindingResult bindingResult){
        if (bindingResult.hasErrors()) {
            return "habits/form"; // re-render form with errors
        }
        habitRepository.save(habit);
        return "redirect:/habits"; // PRG pattern: Post -> Redirect -> Get
    }

    // mark a habit as done today
    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id) {
        habitService.markDoneToday(id);
        return "redirect:/habits";
    }

}
