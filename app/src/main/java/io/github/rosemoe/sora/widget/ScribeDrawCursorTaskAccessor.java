package io.github.rosemoe.sora.widget;

import androidx.annotation.Nullable;

/**
 * Package-private bridge in {@code io.github.rosemoe.sora.widget} providing direct
 * zero-reflection access to {@link EditorRenderer.DrawCursorTask#y} for Pass-2
 * cursor/handle translation during selection-deletion gap collapse.
 */
public final class ScribeDrawCursorTaskAccessor {

    private ScribeDrawCursorTaskAccessor() {
    }

    public static void addOffsetY(@Nullable EditorRenderer.DrawCursorTask task, float deltaY) {
        if (task != null) {
            task.y += deltaY;
        }
    }

    public static float getY(@Nullable EditorRenderer.DrawCursorTask task) {
        return task != null ? task.y : 0f;
    }

    public static boolean isImeConsumingInput(@Nullable CodeEditor editor) {
        return editor != null && editor.inputConnection != null && editor.inputConnection.imeConsumingInput;
    }
}
