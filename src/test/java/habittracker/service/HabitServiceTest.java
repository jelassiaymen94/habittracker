package habittracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import habittracker.model.Habit;
import habittracker.model.HabitCompletion;
import habittracker.repository.HabitCompletionRepository;
import habittracker.repository.HabitRepository;

/**
 * Unit tests for the core streak / completion logic in {@link HabitService}.
 * The repositories are mocked so the tests exercise the algorithms in isolation,
 * with no database involved.
 */
@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

    @Mock
    private HabitRepository habitRepository;

    @Mock
    private HabitCompletionRepository completionRepository;

    @InjectMocks
    private HabitService habitService;

    private final Habit habit = new Habit();

    /** Build a completion row for a given date (only the date matters for these tests). */
    private static HabitCompletion completionOn(LocalDate date) {
        HabitCompletion c = new HabitCompletion();
        c.setDate(date);
        return c;
    }

    /** Stub the "newest-first" query used by the streak helpers. */
    private void stubCompletions(LocalDate... datesNewestFirst) {
        List<HabitCompletion> rows = Arrays.stream(datesNewestFirst)
                .map(HabitServiceTest::completionOn)
                .toList();
        when(completionRepository.findByHabitOrderByDateDesc(habit)).thenReturn(rows);
    }

    // ---------------------------------------------------------------------
    // currentStreak
    // ---------------------------------------------------------------------

    @Test
    void currentStreak_isZero_whenNoHistory() {
        stubCompletions();
        assertThat(habitService.currentStreak(habit)).isZero();
    }

    @Test
    void currentStreak_countsConsecutiveDaysEndingToday() {
        LocalDate today = LocalDate.now();
        stubCompletions(today, today.minusDays(1), today.minusDays(2));
        assertThat(habitService.currentStreak(habit)).isEqualTo(3);
    }

    @Test
    void currentStreak_allowsGraceWhenLastDoneYesterday() {
        LocalDate today = LocalDate.now();
        stubCompletions(today.minusDays(1), today.minusDays(2));
        assertThat(habitService.currentStreak(habit)).isEqualTo(2);
    }

    @Test
    void currentStreak_isZero_whenMostRecentIsOlderThanYesterday() {
        LocalDate today = LocalDate.now();
        stubCompletions(today.minusDays(2), today.minusDays(3));
        assertThat(habitService.currentStreak(habit)).isZero();
    }

    @Test
    void currentStreak_stopsAtFirstGap() {
        LocalDate today = LocalDate.now();
        // today, yesterday, then a gap (skips day 2), then day 3
        stubCompletions(today, today.minusDays(1), today.minusDays(3), today.minusDays(4));
        assertThat(habitService.currentStreak(habit)).isEqualTo(2);
    }

    // ---------------------------------------------------------------------
    // longestStreak
    // ---------------------------------------------------------------------

    @Test
    void longestStreak_isZero_whenNoHistory() {
        stubCompletions();
        assertThat(habitService.longestStreak(habit)).isZero();
    }

    @Test
    void longestStreak_findsLongestRunAcrossGaps() {
        LocalDate today = LocalDate.now();
        // A run of 2 (days 10-9), then a run of 4 (days 5-2): longest = 4
        stubCompletions(
                today.minusDays(9), today.minusDays(10),
                today.minusDays(2), today.minusDays(3), today.minusDays(4), today.minusDays(5));
        assertThat(habitService.longestStreak(habit)).isEqualTo(4);
    }

    @Test
    void longestStreak_deduplicatesRepeatedDates() {
        LocalDate today = LocalDate.now();
        stubCompletions(today, today, today.minusDays(1));
        assertThat(habitService.longestStreak(habit)).isEqualTo(2);
    }

    // ---------------------------------------------------------------------
    // isDoneToday / totalCompletions
    // ---------------------------------------------------------------------

    @Test
    void isDoneToday_reflectsRepositoryLookup() {
        when(completionRepository.findByHabitAndDate(habit, LocalDate.now()))
                .thenReturn(Optional.of(new HabitCompletion()));
        assertThat(habitService.isDoneToday(habit)).isTrue();
    }

    @Test
    void totalCompletions_delegatesToCountQuery() {
        when(completionRepository.countByHabit(habit)).thenReturn(7L);
        assertThat(habitService.totalCompletions(habit)).isEqualTo(7L);
    }

    // ---------------------------------------------------------------------
    // markDoneToday (idempotency)
    // ---------------------------------------------------------------------

    @Test
    void markDoneToday_savesCompletion_whenNotYetDone() {
        long habitId = 42L;
        when(habitRepository.findById(habitId)).thenReturn(Optional.of(habit));
        when(completionRepository.findByHabitAndDate(habit, LocalDate.now()))
                .thenReturn(Optional.empty());

        habitService.markDoneToday(habitId);

        verify(completionRepository).save(any(HabitCompletion.class));
    }

    @Test
    void markDoneToday_isIdempotent_whenAlreadyDone() {
        long habitId = 42L;
        when(habitRepository.findById(habitId)).thenReturn(Optional.of(habit));
        when(completionRepository.findByHabitAndDate(habit, LocalDate.now()))
                .thenReturn(Optional.of(new HabitCompletion()));

        habitService.markDoneToday(habitId);

        verify(completionRepository, never()).save(any(HabitCompletion.class));
    }

    @Test
    void markDoneToday_throws_whenHabitMissing() {
        when(habitRepository.findById(99L)).thenReturn(Optional.empty());
        try {
            habitService.markDoneToday(99L);
        } catch (IllegalArgumentException expected) {
            assertThat(expected).hasMessageContaining("Habit not found");
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    // ---------------------------------------------------------------------
    // last90Days
    // ---------------------------------------------------------------------

    @Test
    void last90Days_returns90CellsOldestFirstWithCorrectFlags() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(89);
        when(completionRepository.findByHabitAndDateBetween(eq(habit), eq(start), eq(today)))
                .thenReturn(List.of(completionOn(today), completionOn(today.minusDays(10))));

        List<HabitService.DayCell> cells = habitService.last90Days(habit);

        assertThat(cells).hasSize(90);
        assertThat(cells.get(0).date()).isEqualTo(start);          // oldest first
        assertThat(cells.get(89).date()).isEqualTo(today);
        assertThat(cells.get(89).completed()).isTrue();            // today is completed
        assertThat(cells.get(79).completed()).isTrue();            // today - 10 is completed
        assertThat(cells.get(88).completed()).isFalse();           // yesterday is not
    }
}
