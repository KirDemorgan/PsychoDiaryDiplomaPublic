package alex.drankov.diary.ai.service;

import alex.drankov.diary.reports.dto.FactResponse;
import alex.drankov.diary.reports.entity.FactType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Programmatic checks of a free-text claim against the facts it cites.
 *
 * <p>The guard does not judge meaning. It guarantees that every number and date in the text comes from the cited
 * facts, that quotations are verbatim, and that the text contains no diagnostic, causal or treatment wording.
 * Semantic support is measured by the research protocol, not asserted here.
 */
final class NarrativeGuard {
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
    private static final Pattern ISO_DATE = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    private static final Pattern DOTTED_DATE = Pattern.compile("\\b(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})\\b");
    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("января", 1), Map.entry("февраля", 2), Map.entry("марта", 3), Map.entry("апреля", 4),
            Map.entry("мая", 5), Map.entry("июня", 6), Map.entry("июля", 7), Map.entry("августа", 8),
            Map.entry("сентября", 9), Map.entry("октября", 10), Map.entry("ноября", 11), Map.entry("декабря", 12));
    private static final Pattern WORD_DATE = Pattern.compile(
            "(?<!\\d)(\\d{1,2})\\s+(" + String.join("|", MONTHS.keySet()) + ")(?!\\p{L})(?:\\s+(\\d{4})(?:\\s+года(?!\\p{L}))?)?",
            FLAGS);
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");
    private static final Pattern QUOTATION = Pattern.compile("«([^»]*)»");
    private static final Pattern NUMBER_WORD = Pattern.compile(
            "(?<!\\p{L})(?:ноль|нуль|два|две|двух|три|трех|трёх|четыре|четырех|четырёх|пять|пяти|шесть|шести|семь|семи"
                    + "|восемь|восьми|девять|девяти|десять|десяти|половин\\p{L}*)(?!\\p{L})", FLAGS);
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?<!\\p{L})(?:диагноз\\p{L}*|диагностир\\p{L}*|расстройств\\p{L}*|депресс\\p{L}*|заболевани\\p{L}*|болезн\\p{L}*"
                    + "|причин\\p{L}*|вследствие|из-за|обусловлен\\p{L}*|приводит\\p{L}*|приводил\\p{L}*|привел\\p{L}*|привело"
                    + "|вызыва\\p{L}*|вызвал\\p{L}*|вызван\\p{L}*|благодаря|лечени\\p{L}*|терапи\\p{L}*|рекоменд\\p{L}*"
                    + "|следует|необходим\\p{L}*|нужно|эффективн\\p{L}*|помога\\p{L}*|помог\\p{L}*|доказ\\p{L}*"
                    + "|увеличить|уменьшить|отменить|назначить|прогноз\\p{L}*)(?!\\p{L})", FLAGS);
    private static final Pattern NO_DATA_REASON = Pattern.compile(
            "из-за (?:отсутствия|недостатка) (?:достаточного количества )?(?:проверенных )?(?:данных|дней)", FLAGS);
    private static final List<String> ALLOWED_DISCLAIMERS = List.of("Это связь, а не доказанная причина.",
            "связь, а не причина", "не доказывает причинность", "не доказывает причинности");

    private NarrativeGuard() {
    }

    static void verify(String text, List<FactResponse> cited, Function<FactResponse, String> allowedText) {
        if (cited.stream().allMatch(fact -> fact.type() == FactType.QUOTE)) {
            throw new IllegalArgumentException("Claim cites only quotations");
        }
        String withoutDisclaimers = text;
        for (String disclaimer : ALLOWED_DISCLAIMERS) {
            withoutDisclaimers = withoutDisclaimers.replace(disclaimer, " ");
        }
        withoutDisclaimers = NO_DATA_REASON.matcher(withoutDisclaimers).replaceAll(" ");
        if (FORBIDDEN.matcher(withoutDisclaimers).find()) {
            throw new IllegalArgumentException("Forbidden wording");
        }
        Set<String> quotes = new HashSet<>();
        Set<String> numbers = new HashSet<>();
        Set<LocalDate> dates = new HashSet<>();
        for (var fact : cited) {
            if (fact.type() == FactType.QUOTE) {
                if (fact.value().quote() != null) {
                    quotes.add(fact.value().quote());
                }
                if (fact.value().date() != null) {
                    dates.add(fact.value().date());
                }
                continue;
            }
            String sentence = allowedText.apply(fact);
            collectDates(sentence, dates);
            numbers.addAll(numbers(stripDates(sentence)));
        }
        for (var date : dates) {
            numbers.add(String.valueOf(date.getDayOfMonth()));
            numbers.add(String.valueOf(date.getMonthValue()));
            numbers.add(String.valueOf(date.getYear()));
        }
        Matcher quotation = QUOTATION.matcher(text);
        while (quotation.find()) {
            if (!quotes.contains(quotation.group(1))) {
                throw new IllegalArgumentException("Unverified quotation");
            }
        }
        String plain = QUOTATION.matcher(text).replaceAll(" ");
        if (NUMBER_WORD.matcher(plain).find()) {
            throw new IllegalArgumentException("Spelled-out number");
        }
        Set<LocalDate> claimed = new HashSet<>();
        collectDates(plain, claimed);
        if (!dates.containsAll(claimed)) {
            throw new IllegalArgumentException("Unsupported date");
        }
        verifyWordDates(plain, dates);
        if (!numbers.containsAll(numbers(stripDates(plain)))) {
            throw new IllegalArgumentException("Unsupported number");
        }
    }

    private static void collectDates(String text, Set<LocalDate> target) {
        Matcher iso = ISO_DATE.matcher(text);
        while (iso.find()) {
            target.add(parse(iso.group(), DateTimeFormatter.ISO_LOCAL_DATE));
        }
        Matcher dotted = DOTTED_DATE.matcher(text);
        while (dotted.find()) {
            target.add(parse("%04d-%02d-%02d".formatted(Integer.parseInt(dotted.group(3)),
                    Integer.parseInt(dotted.group(2)), Integer.parseInt(dotted.group(1))), DateTimeFormatter.ISO_LOCAL_DATE));
        }
    }

    private static void verifyWordDates(String text, Set<LocalDate> allowed) {
        Matcher matcher = WORD_DATE.matcher(text);
        while (matcher.find()) {
            int day = Integer.parseInt(matcher.group(1));
            int month = MONTHS.get(matcher.group(2).toLowerCase(Locale.ROOT));
            if (matcher.group(3) == null) {
                if (allowed.stream().noneMatch(date -> date.getDayOfMonth() == day && date.getMonthValue() == month)) {
                    throw new IllegalArgumentException("Unsupported date");
                }
                continue;
            }
            LocalDate claimed = parse("%04d-%02d-%02d".formatted(Integer.parseInt(matcher.group(3)), month, day),
                    DateTimeFormatter.ISO_LOCAL_DATE);
            if (!allowed.contains(claimed)) {
                throw new IllegalArgumentException("Unsupported date");
            }
        }
    }

    private static LocalDate parse(
            String value,
            DateTimeFormatter formatter
    ) {
        try {
            return LocalDate.parse(value, formatter);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid date", exception);
        }
    }

    private static String stripDates(String text) {
        String withoutDotted = DOTTED_DATE.matcher(ISO_DATE.matcher(text).replaceAll(" ")).replaceAll(" ");
        return WORD_DATE.matcher(withoutDotted).replaceAll(" ");
    }

    private static Set<String> numbers(String text) {
        Set<String> result = new HashSet<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            result.add(new BigDecimal(matcher.group().replace(',', '.')).stripTrailingZeros().toPlainString());
        }
        return result;
    }
}
