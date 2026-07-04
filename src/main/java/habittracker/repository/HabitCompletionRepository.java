package habittracker.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import habittracker.model.Habit;
import habittracker.model.HabitCompletion;

public interface HabitCompletionRepository extends JpaRepository<HabitCompletion, Long> {
    // Find a specific habit completion: "Did this habit get done on this date?"
    Optional<HabitCompletion> findByHabitAndDate(Habit habit, LocalDate date);

    // Get all completions for a habit, newest first
    List<HabitCompletion> findByHabitOrderByDateDesc(Habit habit);

    List<HabitCompletion> findByHabitAndDateBetween(Habit habit, LocalDate start, LocalDate end);

    // Count all completions for a habit without loading the rows into memory
    long countByHabit(Habit habit);
}
