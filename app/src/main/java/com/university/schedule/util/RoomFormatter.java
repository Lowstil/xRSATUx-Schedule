package com.university.schedule.util;

public final class RoomFormatter {

    private static final int C_MAIN   = 0xFF1565C0; // синий
    private static final int C_FIRST  = 0xFF2E7D32; // зелёный
    private static final int C_SECOND = 0xFFEF6C00; // оранжевый
    private static final int C_THIRD  = 0xFF6A1B9A; // фиолетовый
    private static final int C_ONLINE = 0xFF00897B; // бирюзовый
    private static final int C_SPORT  = 0xFFD84315; // терракот
    private static final int C_TOCHKA = 0xFF00838F; // циан
    private static final int C_PRACT  = 0xFF5D4037; // коричневый
    private static final int C_OTHER  = 0xFF616161; // серый

    public static final class RoomInfo {
        public final String building;
        public final String room;
        public final int color;
        public RoomInfo(String b, String r, int c) { building = b; room = r; color = c; }
    }

    public static RoomInfo parse(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        String low = s.toLowerCase();
        if (low.contains("онлайн"))        return new RoomInfo("Онлайн", null, C_ONLINE);
        if (low.contains("спортзал"))      return new RoomInfo("Большой спортзал", null, C_SPORT);
        if (low.contains("точка кипения")) return new RoomInfo("Точка кипения", null, C_TOCHKA);
        if (low.contains("предприят"))     return new RoomInfo("Место практики", null, C_PRACT);

        int dash = s.indexOf('-');
        if (dash <= 0) return new RoomInfo(s, null, C_OTHER);
        String prefix = s.substring(0, dash).trim();
        String room = s.substring(dash + 1).trim();
        if (room.isEmpty()) room = null;

        switch (prefix) {
            case "Г": case "г": return new RoomInfo("Главный корпус", room, C_MAIN);
            case "1":           return new RoomInfo("Первый корпус",  room, C_FIRST);
            case "2":           return new RoomInfo("Второй корпус",  room, C_SECOND);
            case "3":           return new RoomInfo("Третий корпус",  room, C_THIRD);
            default:            return new RoomInfo(s, null, C_OTHER);
        }
    }

    private RoomFormatter() {}
}