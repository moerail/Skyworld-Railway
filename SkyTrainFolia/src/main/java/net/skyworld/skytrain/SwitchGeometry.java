package net.skyworld.skytrain;

import java.util.Locale;

import org.bukkit.block.BlockFace;

enum SwitchGeometry {
    LEFT("left"),
    RIGHT("right"),
    LEGACY_FRONT_LEFT("fl"),
    LEGACY_FRONT_RIGHT("fr");

    final String code;

    SwitchGeometry(String code) {
        this.code = code;
    }

    static SwitchGeometry parse(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "left" -> LEFT;
            case "right" -> RIGHT;
            case "fl", "front-left", "front_left" -> LEGACY_FRONT_LEFT;
            case "fr", "front-right", "front_right" -> LEGACY_FRONT_RIGHT;
            default -> null;
        };
    }

    static SwitchGeometry parseNewDefinition(String value) {
        SwitchGeometry geometry = parse(value);
        return geometry == LEFT || geometry == RIGHT ? geometry : null;
    }

    boolean legacyFrontFacing() {
        return this == LEGACY_FRONT_LEFT || this == LEGACY_FRONT_RIGHT;
    }

    BlockFace frontDivergingFace(BlockFace front) {
        if (!legacyFrontFacing()) {
            throw new IllegalStateException("Only legacy front-facing switches use front diverging faces");
        }
        return this == LEGACY_FRONT_LEFT ? leftOf(front) : rightOf(front);
    }

    BlockFace divergingFace(BlockFace front) {
        if (this == LEFT) {
            return leftOf(front);
        }
        if (this == RIGHT) {
            return rightOf(front);
        }
        throw new IllegalStateException("Only left/right switches use sign-facing diverging faces");
    }

    BlockFace sideEntryFace(BlockFace throughSign) {
        if (this == LEFT) {
            return leftOf(throughSign);
        }
        if (this == RIGHT) {
            return rightOf(throughSign);
        }
        throw new IllegalStateException("Only left/right switches use side-mounted entry faces");
    }

    static BlockFace leftOf(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> throw new IllegalArgumentException("Switch front must be horizontal: " + face);
        };
    }

    static BlockFace rightOf(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> throw new IllegalArgumentException("Switch front must be horizontal: " + face);
        };
    }
}
