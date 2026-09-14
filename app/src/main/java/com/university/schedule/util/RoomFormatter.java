package com.university.schedule.util;

/**
 * Разбирает поле "аудитория" на понятный корпус + номер кабинета и подбирает
 * цвет-код по корпусу — это помогает быстро находить нужную запись взглядом.
 *
 * Палитра продублирована для светлой и тёмной темы. Цвета для светлой темы —
 * тёмные, насыщенные (700-й тон Material) — они читаются как текст и как
 * лёгкая заливка (14% альфы) на белой карточке. Прямое использование тех же
 * значений на тёмной карточке (#1C1F2E) читалось отвратительно: заливка почти
 * сливалась с фоном, а сам текст либо терял контраст, либо "звенел" избыточной
 * яркостью. Для тёмной темы взяты более светлые и чуть менее насыщенные тона
 * (аналог 200–300-го тона Material) — они держат контраст на тёмной
 * поверхности и не режут глаз, а альфа заливки увеличена (22%), потому что на
 * тёмном фоне низкая альфа попросту не видна.
 */
public final class RoomFormatter {

    /** Цвета для СВЕТЛОЙ темы (насыщенные, тёмные — как текст на белом). */
    private static final int L_MAIN   = 0xFF1565C0; // синий
    private static final int L_FIRST  = 0xFF2E7D32; // зелёный
    private static final int L_SECOND = 0xFFEF6C00; // оранжевый
    private static final int L_THIRD  = 0xFF6A1B9A; // фиолетовый
    private static final int L_ONLINE = 0xFF00897B; // бирюзовый
    private static final int L_SPORT  = 0xFFD84315; // терракот
    private static final int L_TOCHKA = 0xFF00838F; // циан
    private static final int L_PRACT  = 0xFF5D4037; // коричневый
    private static final int L_OTHER  = 0xFF616161; // серый

    /** Цвета для ТЁМНОЙ темы (светлее и мягче — читаются на #1C1F2E). */
    private static final int D_MAIN   = 0xFF7EA6E8; // светло-синий
    private static final int D_FIRST  = 0xFF81C995; // светло-зелёный
    private static final int D_SECOND = 0xFFF3A96B; // светло-оранжевый (персик)
    private static final int D_THIRD  = 0xFFC79DE8; // светло-фиолетовый (лаванда)
    private static final int D_ONLINE = 0xFF6FCBBF; // светло-бирюзовый
    private static final int D_SPORT  = 0xFFEF9C8B; // светло-терракотовый (лосось)
    private static final int D_TOCHKA = 0xFF67D3E3; // светло-циан
    private static final int D_PRACT  = 0xFFC9A88A; // светлый тёпло-бежевый
    private static final int D_OTHER  = 0xFFB0B4C4; // светло-серый

    public static final class RoomInfo {
        public final String building;
        public final String room;
        public final int color;
        public RoomInfo(String b, String r, int c) { building = b; room = r; color = c; }
    }

    /** Обратная совместимость: разбор с цветами светлой темы по умолчанию. */
    public static RoomInfo parse(String raw) {
        return parse(raw, false);
    }

    /**
     * Разбор с явным указанием темы.
     * @param dark true — вернуть палитру для тёмной темы, false — для светлой.
     */
    public static RoomInfo parse(String raw, boolean dark) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        String low = s.toLowerCase();

        if (low.contains("онлайн"))        return new RoomInfo("Онлайн", null, dark ? D_ONLINE : L_ONLINE);
        if (low.contains("спортзал"))      return new RoomInfo("Большой спортзал", null, dark ? D_SPORT : L_SPORT);
        if (low.contains("точка кипения")) return new RoomInfo("Точка кипения", null, dark ? D_TOCHKA : L_TOCHKA);
        if (low.contains("предприят"))     return new RoomInfo("Место практики", null, dark ? D_PRACT : L_PRACT);

        int dash = s.indexOf('-');
        if (dash <= 0) return new RoomInfo(s, null, dark ? D_OTHER : L_OTHER);
        String prefix = s.substring(0, dash).trim();
        String room = s.substring(dash + 1).trim();
        if (room.isEmpty()) room = null;

        switch (prefix) {
            case "Г": case "г": return new RoomInfo("Главный корпус", room, dark ? D_MAIN : L_MAIN);
            case "1":           return new RoomInfo("Первый корпус",  room, dark ? D_FIRST : L_FIRST);
            case "2":           return new RoomInfo("Второй корпус",  room, dark ? D_SECOND : L_SECOND);
            case "3":           return new RoomInfo("Третий корпус",  room, dark ? D_THIRD : L_THIRD);
            default:            return new RoomInfo(s, null, dark ? D_OTHER : L_OTHER);
        }
    }

    private RoomFormatter() {}
}
