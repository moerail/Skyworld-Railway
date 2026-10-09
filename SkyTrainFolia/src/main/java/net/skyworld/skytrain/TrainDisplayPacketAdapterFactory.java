package net.skyworld.skytrain;

/** Selects exactly one protocol implementation without linking the other version's bytecode. */
final class TrainDisplayPacketAdapterFactory {
    private TrainDisplayPacketAdapterFactory() { }

    static TrainDisplayPacketAdapter forServer(String minecraftVersion) {
        String suffix = switch (minecraftVersion) {
            case "26.2" -> "26_2";
            case "26.3" -> "26_3";
            default -> throw new IllegalArgumentException("Unsupported display protocol: " + minecraftVersion);
        };
        try {
            String name = TrainDisplayPacketAdapterFactory.class.getPackageName()
                    + ".TrainDisplayPacketAdapter_" + suffix;
            Class<?> type = Class.forName(name, true, TrainDisplayPacketAdapterFactory.class.getClassLoader());
            TrainDisplayPacketAdapter adapter = (TrainDisplayPacketAdapter) type.getDeclaredConstructor().newInstance();
            adapter.checkCompatibility();
            return adapter;
        } catch (ReflectiveOperationException | LinkageError ex) {
            throw new IllegalStateException("Cannot initialize display protocol " + minecraftVersion, ex);
        }
    }
}
