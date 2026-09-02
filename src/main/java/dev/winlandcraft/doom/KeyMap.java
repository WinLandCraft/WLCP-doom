package dev.winlandcraft.doom;

/** GLFW keys -> Mocha Doom's stable PC scancode enum ordinals. */
final class KeyMap {
    static int scan(int key) {
        if (key >= 49 && key <= 57) return key - 47;
        if (key == 48) return 11;
        if (key >= 290 && key <= 300) return key <= 299 ? key - 231 : 87;
        return switch (key) {
            case 65 -> 30; case 66 -> 48; case 67 -> 46; case 68 -> 32;
            case 69 -> 18; case 70 -> 33; case 71 -> 34; case 72 -> 35;
            case 73 -> 23; case 74 -> 36; case 75 -> 37; case 76 -> 38;
            case 77 -> 50; case 78 -> 49; case 79 -> 24; case 80 -> 25;
            case 81 -> 16; case 82 -> 19; case 83 -> 31; case 84 -> 20;
            case 85 -> 22; case 86 -> 47; case 87 -> 17; case 88 -> 45;
            case 89 -> 21; case 90 -> 44;
            case 32 -> 57; case 257, 335 -> 28; case 258 -> 15; case 259 -> 14;
            case 262 -> 106; case 263 -> 105; case 264 -> 108; case 265 -> 103;
            case 340, 344 -> 54; case 341, 345 -> 29; case 342, 346 -> 56;
            case 96, 301 -> 1; // ` or F12 opens Doom's menu; Escape belongs to the host.
            case 284 -> 119; // Pause (checked against upstream ScanCode)
            case 45 -> 12; case 61 -> 13; case 44 -> 51; case 46 -> 52;
            case 47 -> 53; case 59 -> 39; case 39 -> 40; case 91 -> 26;
            case 93 -> 27; case 92 -> 43;
            default -> 0;
        };
    }
    private KeyMap() {}
}
