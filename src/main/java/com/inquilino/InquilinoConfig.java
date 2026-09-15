package com.inquilino;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Configuração simples em config/inquilino/inquilino.properties.
 * Também guarda a "memória" global do Inquilino, que sobrevive entre mundos.
 */
public final class InquilinoConfig {
	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("inquilino");
	private static final Path CONFIG = DIR.resolve("inquilino.properties");
	private static final Path MEMORY = DIR.resolve("memoria.dat");

	/** Flashes rápidos, estática estroboscópica e quadros subliminares. */
	public static boolean flashes = true;
	/** Permite que ele use o nome de usuário do computador (apenas em mundos locais). */
	public static boolean useComputerName = true;
	/** Permite criar arquivos do ARG em .minecraft/o_inquilino/. */
	public static boolean writeFiles = true;
	/** Multiplicador do ritmo da assombração (2.0 = duas vezes mais rápido). */
	public static double pace = 1.0;
	/** Se o aviso inicial já foi aceito. */
	public static boolean warningSeen = false;
	/** Filtro de câmera VHS (gravação). */
	public static boolean vhs = true;
	/** Seu corpo aparece quando você olha para baixo. */
	public static boolean bodyVisible = true;
	/** Câmera de mão: balança com os passos, inclina nas curvas, respira parada. */
	public static boolean cameraMotion = true;
	/** Tocha na mão ilumina ao redor. */
	public static boolean dynamicLight = true;
	/** Neblina que fecha conforme ele se aproxima. */
	public static boolean fog = true;
	/** Silencia a música do jogo enquanto ele estiver no mundo. */
	public static boolean muteMusic = true;

	private InquilinoConfig() {
	}

	public static synchronized void load() {
		Properties p = read(CONFIG);
		flashes = Boolean.parseBoolean(p.getProperty("flashes", "true"));
		useComputerName = Boolean.parseBoolean(p.getProperty("usar_nome_do_computador", "true"));
		writeFiles = Boolean.parseBoolean(p.getProperty("criar_arquivos", "true"));
		warningSeen = Boolean.parseBoolean(p.getProperty("aviso_visto", "false"));
		vhs = Boolean.parseBoolean(p.getProperty("camera_vhs", "true"));
		bodyVisible = Boolean.parseBoolean(p.getProperty("corpo_visivel", "true"));
		cameraMotion = Boolean.parseBoolean(p.getProperty("camera_realista", "true"));
		dynamicLight = Boolean.parseBoolean(p.getProperty("luz_na_mao", "true"));
		fog = Boolean.parseBoolean(p.getProperty("neblina", "true"));
		muteMusic = Boolean.parseBoolean(p.getProperty("silenciar_musica", "true"));
		try {
			pace = Math.max(0.1, Math.min(10.0, Double.parseDouble(p.getProperty("ritmo", "1.0"))));
		} catch (NumberFormatException e) {
			pace = 1.0;
		}
		save();
	}

	public static synchronized void save() {
		Properties p = new Properties();
		p.setProperty("flashes", Boolean.toString(flashes));
		p.setProperty("usar_nome_do_computador", Boolean.toString(useComputerName));
		p.setProperty("criar_arquivos", Boolean.toString(writeFiles));
		p.setProperty("ritmo", Double.toString(pace));
		p.setProperty("aviso_visto", Boolean.toString(warningSeen));
		p.setProperty("camera_vhs", Boolean.toString(vhs));
		p.setProperty("corpo_visivel", Boolean.toString(bodyVisible));
		p.setProperty("camera_realista", Boolean.toString(cameraMotion));
		p.setProperty("luz_na_mao", Boolean.toString(dynamicLight));
		p.setProperty("neblina", Boolean.toString(fog));
		p.setProperty("silenciar_musica", Boolean.toString(muteMusic));
		write(CONFIG, p, "O Inquilino - configuracao. ritmo: 0.1 a 10 (maior = mais rapido)");
	}

	// ------------------------------------------------------------------ memória

	public static synchronized boolean remembers() {
		return Boolean.parseBoolean(read(MEMORY).getProperty("encontrou", "false"));
	}

	public static synchronized String rememberedName() {
		return read(MEMORY).getProperty("nome", "");
	}

	public static synchronized void remember(String name) {
		Properties p = read(MEMORY);
		int times = 0;
		try {
			times = Integer.parseInt(p.getProperty("vezes", "0"));
		} catch (NumberFormatException ignored) {
		}
		p.setProperty("encontrou", "true");
		p.setProperty("nome", name);
		p.setProperty("vezes", Integer.toString(times + 1));
		write(MEMORY, p, "nao apague. ele vai perceber.");
	}

	// ------------------------------------------------------------------ io

	private static Properties read(Path file) {
		Properties p = new Properties();
		if (Files.exists(file)) {
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				p.load(r);
			} catch (IOException e) {
				InquilinoMod.LOGGER.warn("Nao foi possivel ler {}", file, e);
			}
		}
		return p;
	}

	private static void write(Path file, Properties p, String comment) {
		try {
			Files.createDirectories(file.getParent());
			try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				p.store(w, comment);
			}
		} catch (IOException e) {
			InquilinoMod.LOGGER.warn("Nao foi possivel salvar {}", file, e);
		}
	}
}
