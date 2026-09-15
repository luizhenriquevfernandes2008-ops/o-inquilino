import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * Gera todas as texturas do mod de forma procedural.
 * Uso: java tools/TextureGen.java src/main/resources/assets/inquilino/textures
 */
public class TextureGen {
    static final Random R = new Random(1311L);

    public static void main(String[] args) throws Exception {
        File root = new File(args.length > 0 ? args[0] : "out");
        new File(root, "entity").mkdirs();
        new File(root, "gui").mkdirs();
        new File(root, "painting").mkdirs();

        ImageIO.write(skin(), "png", new File(root, "entity/inquilino.png"));
        ImageIO.write(eyes(), "png", new File(root, "entity/inquilino_eyes.png"));
        BufferedImage face = jumpscare();
        ImageIO.write(face, "png", new File(root, "gui/jumpscare.png"));
        ImageIO.write(noise(256), "png", new File(root, "gui/static.png"));
        ImageIO.write(vignette(256), "png", new File(root, "gui/vignette.png"));
        ImageIO.write(portrait(), "png", new File(root, "painting/retrato.png"));
        ImageIO.write(portraitHallway(), "png", new File(root, "painting/corredor.png"));
        File icon = new File(root.getParentFile(), "icon.png");
        ImageIO.write(icon(face), "png", icon);
        System.out.println("Texturas geradas em " + root.getAbsolutePath());
    }

    // ---------------------------------------------------------------- utils
    static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    static int shade(int rgb, int delta) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return argb(255, r + delta, g + delta, b + delta);
    }

    static int jitter(int rgb, int amount) {
        return shade(rgb, R.nextInt(amount * 2 + 1) - amount);
    }

    static void rect(BufferedImage img, int x, int y, int w, int h, int rgb, int noise) {
        for (int i = x; i < x + w; i++)
            for (int j = y; j < y + h; j++)
                img.setRGB(i, j, noise > 0 ? jitter(rgb, noise) : (0xFF000000 | rgb));
    }

    // ---------------------------------------------------------------- skin
    // Layout customizado (ver TenantModel): cabeça(0,0) 8x8x8, corpo(16,16) 8x14x4,
    // braço(40,16) 3x16x3, perna(0,16) 4x14x4.
    static final int SHIRT = 0x262a30, PANTS = 0x17140f, SKIN = 0x7d7b74, SKIN_DARK = 0x4a4843;

    static BufferedImage skin() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        // cabeça inteira: pele doente
        rect(img, 0, 0, 32, 16, SKIN, 10);
        // cabelo ralo e escuro no topo e atrás
        rect(img, 8, 0, 8, 8, 0x14110e, 6);
        rect(img, 24, 8, 8, 8, 0x14110e, 6);
        for (int i = 0; i < 8; i++) {
            int len = 1 + R.nextInt(3);
            for (int k = 0; k < len; k++) {
                img.setRGB(i, 8 + k, jitter(0x14110e, 5));
                img.setRGB(16 + i, 8 + k, jitter(0x14110e, 5));
            }
            img.setRGB(8 + i, 8, jitter(0x14110e, 5));
        }
        // rosto apagado: estática cinza, como um arquivo corrompido
        for (int x = 8; x < 16; x++)
            for (int y = 9; y < 16; y++) {
                int g = 60 + R.nextInt(120);
                img.setRGB(x, y, argb(255, g, g, g - 6));
            }
        // olhos: buracos pretos, um mais baixo que o outro
        rect(img, 9, 11, 2, 2, 0x000000, 0);
        rect(img, 13, 12, 2, 2, 0x000000, 0);
        // boca: um risco vertical escorrendo
        img.setRGB(12, 13, 0xFF000000);
        img.setRGB(12, 14, 0xFF000000);
        img.setRGB(11, 14, 0xFF050505);
        img.setRGB(12, 15, 0xFF0A0000);

        // corpo: camisa velha e rasgada
        rect(img, 16, 16, 24, 18, SHIRT, 7);
        // manchas escuras e rasgos
        for (int i = 0; i < 40; i++) {
            int x = 16 + R.nextInt(24), y = 16 + R.nextInt(18);
            img.setRGB(x, y, jitter(0x0e0f12, 4));
        }
        // gola aberta com pele
        rect(img, 23, 20, 2, 2, SKIN_DARK, 6);
        // barra da camisa rasgada
        for (int x = 20; x < 28; x++) if (R.nextBoolean()) img.setRGB(x, 33, jitter(PANTS, 4));
        // mancha antiga (ferrugem) no peito
        rect(img, 25, 24, 2, 3, 0x2e1a14, 4);

        // braços: manga até metade, depois pele, dedos escurecidos e longos
        rect(img, 40, 16, 12, 19, SHIRT, 7);
        for (int x = 40; x < 52; x++) {
            for (int y = 26; y < 35; y++) img.setRGB(x, y, jitter(SKIN, 9));
            for (int y = 32; y < 35; y++) img.setRGB(x, y, jitter(0x1a1816, 5));
        }
        // topo/fundo do braço
        rect(img, 43, 16, 3, 3, SHIRT, 5);
        rect(img, 46, 16, 3, 3, 0x1a1816, 4);

        // pernas: calça escura, pés descalços
        rect(img, 0, 16, 16, 18, PANTS, 5);
        for (int x = 0; x < 16; x++)
            for (int y = 31; y < 34; y++) img.setRGB(x, y, jitter(SKIN_DARK, 7));
        rect(img, 8, 16, 4, 4, SKIN_DARK, 6);
        return img;
    }

    static BufferedImage eyes() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        // apenas um ponto de luz dentro de cada buraco
        img.setRGB(10, 12, 0xFFFFFFFF);
        img.setRGB(13, 12, 0xFFE8E8E8);
        img.setRGB(9, 11, 0x40FFFFFF);
        img.setRGB(14, 13, 0x40FFFFFF);
        return img;
    }

    // ---------------------------------------------------------------- jumpscare
    static BufferedImage jumpscare() {
        int s = 64; // resolução "pixel art", depois ampliada 4x
        BufferedImage small = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        double cx = 31.5, cy = 30;
        for (int x = 0; x < s; x++)
            for (int y = 0; y < s; y++) {
                double dx = (x - cx) / 27.0, dy = (y - cy) / 33.0;
                double d = dx * dx + dy * dy;
                if (d < 1.0) {
                    int base = 150 - (int) (d * 90);
                    int g = base + R.nextInt(40) - 20;
                    small.setRGB(x, y, argb(255, g, g - 3, g - 10));
                } else {
                    int g = R.nextInt(14);
                    small.setRGB(x, y, argb(255, g, g, g));
                }
            }
        // veias
        for (int v = 0; v < 14; v++) {
            double x = cx + R.nextGaussian() * 12, y = cy + R.nextGaussian() * 14;
            for (int k = 0; k < 18; k++) {
                x += R.nextGaussian() * 0.9;
                y += R.nextGaussian() * 0.9;
                int xi = (int) x, yi = (int) y;
                if (xi >= 0 && yi >= 0 && xi < s && yi < s && (small.getRGB(xi, yi) & 0xFF) > 40)
                    small.setRGB(xi, yi, argb(255, 70, 45, 48));
            }
        }
        // órbitas: ovais pretas enormes e assimétricas
        ellipse(small, 20, 24, 7.5, 9.5, 0xFF000000);
        ellipse(small, 43, 26, 7.0, 10.5, 0xFF000000);
        // escorrido embaixo dos olhos
        for (int y = 32; y < 46; y++) {
            if (R.nextInt(5) > 0) small.setRGB(19 + (y % 3 == 0 ? 1 : 0), y, 0xFF050303);
            if (R.nextInt(4) > 0) small.setRGB(44, y + 2 < s ? y + 2 : y, 0xFF050303);
        }
        // pupilas: um único ponto branco em cada
        small.setRGB(21, 25, 0xFFFFFFFF);
        small.setRGB(42, 28, 0xFFFFFFFF);
        // boca: abertura vertical impossível
        ellipse(small, 32, 50, 6.5, 11.5, 0xFF000000);
        for (int x = 26; x < 39; x++) {
            if (R.nextInt(3) == 0) small.setRGB(x, 40 + R.nextInt(2), argb(255, 190, 185, 160));
            if (R.nextInt(3) == 0) small.setRGB(x, 60 + R.nextInt(2), argb(255, 170, 165, 140));
        }
        // glitch: linhas deslocadas
        BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 256; x++)
            for (int y = 0; y < 256; y++)
                img.setRGB(x, y, small.getRGB(x / 4, y / 4));
        for (int i = 0; i < 9; i++) {
            int y0 = R.nextInt(250), h = 2 + R.nextInt(6), off = R.nextInt(40) - 20;
            for (int y = y0; y < Math.min(256, y0 + h); y++) {
                int[] row = new int[256];
                for (int x = 0; x < 256; x++) row[x] = img.getRGB(Math.floorMod(x - off, 256), y);
                for (int x = 0; x < 256; x++) {
                    int c = row[x];
                    if (R.nextInt(6) == 0) c = (c & 0xFF00FFFF) | ((R.nextInt(80)) << 16);
                    img.setRGB(x, y, c);
                }
            }
        }
        return img;
    }

    static void ellipse(BufferedImage img, double cx, double cy, double rx, double ry, int argb) {
        for (int x = 0; x < img.getWidth(); x++)
            for (int y = 0; y < img.getHeight(); y++) {
                double dx = (x - cx) / rx, dy = (y - cy) / ry;
                double d = dx * dx + dy * dy;
                if (d < 1.0 - R.nextDouble() * 0.12) img.setRGB(x, y, argb);
            }
    }

    // ---------------------------------------------------------------- overlays
    static BufferedImage noise(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < s; x++)
            for (int y = 0; y < s; y++) {
                int g = R.nextInt(256);
                img.setRGB(x, y, argb(255, g, g, g));
            }
        // algumas linhas de varredura mais claras
        for (int y = 0; y < s; y += 3 + R.nextInt(9))
            for (int x = 0; x < s; x++) img.setRGB(x, y, argb(255, 200 + R.nextInt(56), 200 + R.nextInt(56), 200 + R.nextInt(56)));
        return img;
    }

    static BufferedImage vignette(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        double c = (s - 1) / 2.0;
        for (int x = 0; x < s; x++)
            for (int y = 0; y < s; y++) {
                double dx = (x - c) / c, dy = (y - c) / c;
                double d = Math.sqrt(dx * dx + dy * dy) / Math.sqrt(2);
                double t = Math.max(0, (d - 0.25) / 0.75);
                t = t * t * (3 - 2 * t);
                img.setRGB(x, y, argb((int) (t * 255), 0, 0, 0));
            }
        return img;
    }

    // ---------------------------------------------------------------- pinturas
    static BufferedImage portrait() {
        // 2x2 blocos = 32x32: um retrato de família com a figura sem rosto
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        rect(img, 0, 0, 32, 32, 0x3b2a1c, 6); // moldura
        rect(img, 2, 2, 28, 28, 0x2d3530, 8); // fundo esverdeado sujo
        // papel de parede listrado
        for (int x = 2; x < 30; x += 4) for (int y = 2; y < 30; y++) img.setRGB(x, y, jitter(0x26302b, 5));
        // silhueta
        rect(img, 12, 8, 8, 8, SKIN, 12);
        for (int x = 12; x < 20; x++)
            for (int y = 10; y < 16; y++) {
                int g = 60 + R.nextInt(110);
                img.setRGB(x, y, argb(255, g, g, g));
            }
        rect(img, 13, 11, 2, 2, 0x000000, 0);
        rect(img, 17, 12, 2, 2, 0x000000, 0);
        rect(img, 9, 16, 14, 14, SHIRT, 6);
        rect(img, 15, 16, 2, 3, SKIN_DARK, 4);
        // rachadura no vidro
        int x = 27, y = 3;
        for (int k = 0; k < 20; k++) {
            img.setRGB(x, y, 0xFFB8B8B0);
            x += R.nextInt(3) - 1 - (k % 3 == 0 ? 1 : 0);
            y += 1;
            x = Math.max(2, Math.min(29, x));
        }
        return img;
    }

    static BufferedImage portraitHallway() {
        // 1x2 blocos = 16x32: um corredor escuro com uma porta aberta
        BufferedImage img = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);
        rect(img, 0, 0, 16, 32, 0x2a1d12, 5);
        rect(img, 1, 1, 14, 30, 0x121210, 4);
        // perspectiva do corredor
        for (int y = 1; y < 31; y++) {
            int inset = Math.abs(y - 16) / 3;
            for (int x = 1; x < 1 + Math.max(0, 5 - inset); x++) img.setRGB(x, y, jitter(0x2b2822, 4));
            for (int x = 15 - Math.max(0, 5 - inset); x < 15; x++) img.setRGB(x, y, jitter(0x2b2822, 4));
        }
        // porta ao fundo com luz
        rect(img, 6, 11, 4, 8, 0x8a7a50, 10);
        // figura na porta
        rect(img, 7, 12, 2, 7, 0x050505, 0);
        img.setRGB(7, 13, 0xFFFFFFFF);
        return img;
    }

    static BufferedImage icon(BufferedImage face) {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 128; x++)
            for (int y = 0; y < 128; y++) {
                int c = face.getRGB(x * 2, y * 2);
                int g = (((c >> 16) & 255) + ((c >> 8) & 255) + (c & 255)) / 3;
                g = (int) (g * 0.85);
                img.setRGB(x, y, argb(255, g, g, g));
            }
        return img;
    }
}
