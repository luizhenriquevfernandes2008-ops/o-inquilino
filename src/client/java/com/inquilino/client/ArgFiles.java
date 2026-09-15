package com.inquilino.client;

import com.inquilino.InquilinoConfig;
import com.inquilino.InquilinoMod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;

/**
 * Os arquivos que ele deixa na pasta do jogo (.minecraft/o_inquilino). Parte do ARG:
 * um deles esconde, codificadas, as coordenadas do quarto.
 * Nada é escrito fora da pasta do jogo.
 */
public final class ArgFiles {
	private ArgFiles() {
	}

	public static void write(Minecraft mc, String id, String arg) {
		if (!InquilinoConfig.writeFiles) {
			return;
		}
		Path dir = mc.gameDirectory.toPath().resolve("o_inquilino");
		try {
			Files.createDirectories(dir);
			switch (id) {
				case "leia" -> writeOnce(dir.resolve(tr("file.inquilino.leia.name")), HorrorEffects.safeFormat(tr("file.inquilino.leia"), arg));
				case "memoria" -> writeOnce(dir.resolve(tr("file.inquilino.memoria.name")), memory(arg));
				case "ultimo" -> writeOnce(dir.resolve(tr("file.inquilino.ultimo.name")), HorrorEffects.safeFormat(tr("file.inquilino.ultimo"), arg));
				default -> {
				}
			}
		} catch (IOException e) {
			InquilinoMod.LOGGER.warn("Nao foi possivel escrever o arquivo {}", id, e);
		}
	}

	private static String tr(String key) {
		return Language.getInstance().getOrDefault(key);
	}

	private static void writeOnce(Path file, String content) throws IOException {
		if (!Files.exists(file)) {
			Files.writeString(file, content.replace("\n", System.lineSeparator()), StandardCharsets.UTF_8);
		}
	}

	/**
	 * Um "despejo de memória": linhas em hexadecimal que, decodificadas, dão uma dica;
	 * e uma única linha em base64 (a que termina com "=") com as coordenadas do quarto.
	 */
	private static String memory(String coords) {
		String[] xz = coords.split(",");
		String secret = "X " + xz[0] + " Z " + (xz.length > 1 ? xz[1] : "0") + " ";
		while (secret.length() % 3 != 1) {
			secret += ".";
		}
		String b64 = Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.US_ASCII));
		byte[] hint = tr("file.inquilino.memoria.hint").getBytes(StandardCharsets.UTF_8);

		Random r = new Random(coords.hashCode());
		StringBuilder sb = new StringBuilder();
		sb.append(tr("file.inquilino.memoria.header")).append("\n\n");
		int addr = 0x7F3A00;
		int hi = 0;
		for (int line = 0; line < 22; line++) {
			sb.append(String.format("0x%06X  ", addr));
			for (int i = 0; i < 16; i++) {
				int b;
				if (line >= 4 && line < 4 + (hint.length + 15) / 16 && hi < hint.length) {
					b = hint[hi++] & 0xFF;
				} else {
					b = r.nextInt(256);
				}
				sb.append(String.format("%02x ", b));
			}
			sb.append("\n");
			addr += 16;
			if (line == 13) {
				sb.append("0x").append(String.format("%06X", addr)).append("  ").append(b64).append("\n");
				addr += 16;
			}
		}
		sb.append("\n").append(tr("file.inquilino.memoria.footer")).append("\n");
		return sb.toString();
	}
}
