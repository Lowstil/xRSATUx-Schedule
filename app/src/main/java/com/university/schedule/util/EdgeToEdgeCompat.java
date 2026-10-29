package com.university.schedule.util;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Совместимость с принудительным edge-to-edge на Android 15+ (API 35+).
 *
 * ПРОБЛЕМА: начиная с targetSdk 35, атрибуты темы android:statusBarColor и
 * android:navigationBarColor (уже выставленные в themes.xml) перестают
 * применяться так, как раньше — платформа принудительно рисует контент
 * "под" системными панелями (edge-to-edge), а не оставляет под них отступ.
 * Без явной обработки это означает, что верхняя часть тулбара может
 * оказаться под статус-баром, а нижние элементы — под навигационной
 * панелью или жестовой полосой.
 *
 * РЕШЕНИЕ здесь — минимальное, не редизайн: вместо полноценного
 * edge-to-edge UI просто добавляем отступы (padding) на корневой View
 * экрана, равные размеру системных панелей — это сохраняет прежний
 * визуальный вид приложения на Android 15+.
 *
 * Использовать так: EdgeToEdgeCompat.applyEdgeToEdgeInsets(this) сразу
 * после setContentView() в каждой Activity.
 */
public final class EdgeToEdgeCompat {

    private EdgeToEdgeCompat() {
    }

    public static void applyEdgeToEdgeInsets(View root) {
        if (root == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    /**
     * Удобный вызов для Activity, чьи layout-файлы не имеют явного android:id
     * на корневом элементе — берёт фактический корень содержимого через
     * android.R.id.content, куда setContentView() кладёт инфлейтнутый layout.
     */
    public static void applyEdgeToEdgeInsets(android.app.Activity activity) {
        if (activity == null) return;
        View content = activity.findViewById(android.R.id.content);
        if (content instanceof android.view.ViewGroup && ((android.view.ViewGroup) content).getChildCount() > 0) {
            applyEdgeToEdgeInsets(((android.view.ViewGroup) content).getChildAt(0));
        }
    }
}
