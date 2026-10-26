package net.skyworld.skytrain;

/** Keeps the original diagnostic for logs/callers and a separate Russian presentation. */
final class UiException extends IllegalArgumentException {
    private final String russian;

    UiException(String original, String russian) {
        super(original);
        this.russian = russian;
    }

    String text(UiLanguage language) {
        return UiMessages.literal(language, getMessage(), russian);
    }
}
