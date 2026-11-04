package dev.itsdropper.vantaloader.bootstrap;

import java.nio.file.Path;

/** Command-line entry point for the standalone VantaLoader runtime. */
public final class VantaLoaderMain {
    private VantaLoaderMain() { }

    public static void main(String[] args) throws Exception {
        Arguments parsed = Arguments.parse(args);
        new VantaLoader(parsed.gameDirectory(), parsed.modsDirectory()).load();
    }

    private record Arguments(Path gameDirectory, Path modsDirectory) {
        private static Arguments parse(String[] args) {
            Path game = Path.of(".");
            Path mods = Path.of("mods");
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--game-dir" -> {
                        if (++i >= args.length) throw new IllegalArgumentException("Missing value for --game-dir");
                        game = Path.of(args[i]);
                    }
                    case "--mods-dir" -> {
                        if (++i >= args.length) throw new IllegalArgumentException("Missing value for --mods-dir");
                        mods = Path.of(args[i]);
                    }
                    case "--help", "-h" -> {
                        System.out.println("VantaLoader " + dev.itsdropper.vantaloader.metadata.LoaderVersion.VERSION);
                        System.out.println("Usage: gradle run --args='[--game-dir <path>] [--mods-dir <path>]'");
                        throw new IllegalArgumentException("Help requested");
                    }
                    default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
                }
            }
            return new Arguments(game, mods);
        }
    }
}
