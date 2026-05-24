package com.kuaishou.intelligentanalysisplatform.application.ai.foundation.provider;

import java.util.function.Consumer;

public record AiStreamCallbacks(
        Consumer<String> onToken,
        Runnable onComplete,
        Consumer<Throwable> onError) {
}
