package habittracker.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import habittracker.model.Habit;
import habittracker.model.HabitCompletion;
import habittracker.repository.HabitCompletionRepository;
import habittracker.repository.HabitRepository;

@Service
public class HabitService {

    private final HabitRepository habitRepository;
    private final HabitCompletionRepository completionRepository;

    public HabitService(HabitRepository habitRepository, HabitCompletionRepository completionRepository) {
        this.habitRepository = habitRepository;
        this.completionRepository = completionRepository;
    }

    /**
     * Mark a habit as done for TODAY.
     * If it's already marked done today, do nothing (idempotent).
     */

    @Transactional
    public void markDoneToday(Long habitId) {
        System.out.println(">>> markDoneToday called for habit id = " + habitId);

        Habit habit = habitRepository.findById(habitId)
                .orElseThrow(() -> new IllegalArgumentException("Habit not found: " + habitId));
        System.out.println(">>> Found habit: " + habit.getName());

        LocalDate today = LocalDate.now();
        System.out.println(">>> Today (Java side) = " + today);

        boolean alreadyDone = completionRepository
                .findByHabitAndDate(habit, today)
                .isPresent();
        System.out.println(">>> alreadyDone = " + alreadyDone);

        if (alreadyDone) {
            System.out.println(">>> SKIPPING (already done)");
            return;
        }

        HabitCompletion completion = new HabitCompletion();
        completion.setHabit(habit);
        completion.setDate(today);
        HabitCompletion saved = completionRepository.save(completion);
        System.out.println(">>> SAVED completion id = " + saved.getId());
    }

    public boolean isDoneToday(Habit habit) {
        return completionRepository.findByHabitAndDate(habit, LocalDate.now()).isPresent();
    }

    /**
     * Total number of days this habit has been completed
     */
    public long totalCompletions(Habit habit) {
        return completionRepository.findByHabitOrderByDateDesc(habit).size();
    }

    public int currentStreak(Habit habit) {
        List<LocalDate> dates = sortedCompletionDatesDesc(habit);
        if (dates.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now();
        LocalDate mostRecent = dates.get(0);

        // Streak is broken if last completion is older than yesterday
        if (mostRecent.isBefore(today.minusDays(1))) {
            return 0;
        }

        int streak = 1;
        // Walk backwards from the most recent date
        for (int i = 1; i < dates.size(); i++) {
            LocalDate prev = dates.get(i - 1);
            LocalDate curr = dates.get(i);
            if (prev.minusDays(1).equals(curr)) {
                streak++;
            } else {
                break; // gap found
            }
        }
        return streak;
    }

    /**
     * Longest streak across the entire history.
     */
    public int longestStreak(Habit habit) {
        List<LocalDate> dates = sortedCompletionDatesDesc(habit);
        if (dates.isEmpty()) {
            return 0;
        }
        int longest = 1;
        int current = 1;

        // Walk from oldest (end) to newest (start)
        for (int i = dates.size() - 2; i >= 0; i--) {
            LocalDate prev = dates.get(i + 1);
            LocalDate curr = dates.get(i);
            if (prev.plusDays(1).equals(curr)) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 1;
            }
        }
        return longest;
    }

    /** Helper: just the dates, sorted newest-first, deduplicated. */
    private List<LocalDate> sortedCompletionDatesDesc(Habit habit) {
        return completionRepository.findByHabitOrderByDateDesc(habit).stream()
                .map(HabitCompletion::getDate)
                .distinct()
                .toList();
    }

    /**
     * Returns the last 90 days as a list of (date, completed?) pairs,
     * oldest first -> easy to render as a grid.
     */
    public List<DayCell> last90Days(Habit habit){
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(89);

        Set<LocalDate> done = completionRepository
                .findByHabitAndDateBetween(habit, start, today).stream()
                .map(HabitCompletion::getDate)
                .collect(java.util.stream.Collectors.toSet());

        List<DayCell> cells = new java.util.ArrayList<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            cells.add(new DayCell(d, done.contains(d)));
        }
        return cells;
    }

    /** A single calendar cell. Java records = immutable mini-class with auto getters. */
    public record DayCell(LocalDate date, boolean completed) {}
}
