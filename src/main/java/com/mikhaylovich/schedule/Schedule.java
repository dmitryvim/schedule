package com.mikhaylovich.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Period;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.mikhaylovich.schedule.Schedule.ScheduledPeriodUnit.*;
import static java.lang.Math.max;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toSet;

public class Schedule {

    private final LocalDate startDate;
    private final ScheduledPeriod period;
    private final Set<MonthDay> periodDays;

    private Schedule(LocalDate startDate, ScheduledPeriodUnit periodUnit, Integer periodCount, Set<MonthDay> periodDays) {
        this.startDate = startDate;
        this.period = new ScheduledPeriod(periodUnit, periodCount);
        this.periodDays = periodDays;
    }

    public static Schedule daily(LocalDate startDate) {
        return new Schedule(startDate, DAY, 1, emptySet());
    }

    public static Schedule weekly(LocalDate startDate, DayOfWeek... days) {
        final var periodDays = Stream.of(days).map(DayOfWeek::getValue).map(i -> MonthDay.of(0, i)).collect(toSet());
        return new Schedule(startDate, WEEK, 1, periodDays);
    }

    public static Schedule weekly(LocalDate startDate, int... days) {
        final var periodDays = IntStream.of(days).boxed().map(i -> MonthDay.of(0, i)).collect(toSet());
        return new Schedule(startDate, WEEK, 1, periodDays);
    }

    public static Schedule weekly(LocalDate startDate) {
        return new Schedule(startDate, WEEK, 1, emptySet());
    }

    public static Schedule monthly(LocalDate startDate, int... days) {
        final var periodDays = days.length > 0
            ? IntStream.of(days).mapToObj(i -> MonthDay.of(0, i)).collect(toSet())
            : singleton(MonthDay.of(0, startDate.getDayOfMonth()));
        return new Schedule(startDate, MONTH, 1, periodDays);
    }

    public static Schedule yearly(LocalDate startDate, MonthDay... days) {
        final var periodDays = Stream.of(days).collect(toSet());
        return new Schedule(startDate, YEAR, 1, periodDays);
    }


    public Schedule next() {
        return new Schedule(nextStartDate(), period.unit, period.amount, periodDays);
    }

//    public Schedule next(Predicate<LocalDate> exclusions);

//    public Schedule nextPreventing(Set<DayOfWeek> weekDayExclusions);

    public LocalDate executeDate() {
        final LocalDate startOfPeriod = period.unit.atStartOfPeriod(startDate);
        return periodDays.stream()
            .map(monthDay -> monthDay.sinceStartOfPeriod(startOfPeriod, period))
            .filter(date -> !date.isBefore(startDate))
            .min(LocalDate::compareTo)
            .or(() -> periodDays.stream()
                .min(MonthDay::compareTo)
                .map(monthDay -> monthDay.sinceStartOfPeriod(startOfPeriod.plus(period.unit.period()), period)))
            .orElse(startDate);
    }

    private LocalDate nextStartDate() {
        return periodDays.isEmpty() ? period.addTo(startDate) : executeDate().plusDays(1);
    }

    public enum ScheduledPeriodUnit {
        DAY(MonthDay.of(0, 1), identity(), __ -> 1),
        WEEK(MonthDay.of(0, 7), date -> date.minusDays(date.getDayOfWeek().getValue() - 1), __ -> 7),
        MONTH(MonthDay.of(1, 0), date -> date.withDayOfMonth(1), LocalDate::lengthOfMonth),
        YEAR(MonthDay.of(12, 0), date -> date.withDayOfYear(1), LocalDate::lengthOfYear);

        public final MonthDay monthDay;
        public final Function<LocalDate, LocalDate> atStartOfFunction;
        public final Function<LocalDate, Integer> maxDaysInPeriod;

        ScheduledPeriodUnit(MonthDay monthDay, Function<LocalDate, LocalDate> atStartOfFunction, Function<LocalDate, Integer> maxDaysInPeriod) {
            this.monthDay = monthDay;
            this.atStartOfFunction = atStartOfFunction;
            this.maxDaysInPeriod = maxDaysInPeriod;
        }

        private LocalDate atStartOfPeriod(LocalDate date) {
            return atStartOfFunction.apply(date);
        }

        private Period period() {
            return monthDay.period();
        }

    }

    private static class ScheduledPeriod {
        private final ScheduledPeriodUnit unit;
        private final int amount;

        private ScheduledPeriod(ScheduledPeriodUnit unit, int amount) {
            this.unit = unit;
            this.amount = amount;
        }

        private LocalDate addTo(LocalDate date) {
            return date.plus(unit.period());
        }
    }

    private static class MonthDay implements Comparable<MonthDay> {
        private final int months;
        private final int days;

        private MonthDay(int months, int days) {
            this.months = months;
            this.days = days;
        }

        private static MonthDay of(int months, int days) {
            return new MonthDay(months, days);
        }


        private Period period() {
            return Period.of(0, months, days);
        }

        private LocalDate sinceStartOfPeriod(LocalDate date, ScheduledPeriod period) {
            return date.plusDays(max(days, period.unit.maxDaysInPeriod.apply(date)) - 1).plusMonths(months);
        }

        @Override
        public int compareTo(MonthDay other) {
            return months == other.months ? days - other.days : months - other.months;
        }
    }
}
