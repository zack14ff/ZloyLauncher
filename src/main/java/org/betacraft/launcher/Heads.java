package org.betacraft.launcher;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import uk.betacraft.auth.Credentials;

/** Player heads for the account lists: the face (and hat layer) cut out of the skin */
public class Heads {
	private static final Map<String, BufferedImage> faces = new ConcurrentHashMap<String, BufferedImage>();
	private static final Set<String> loading = java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	private static BufferedImage steve;

	/**
	 * The head at the given size. A licensed account's real head is fetched in the background:
	 * until then Steve is returned, and whenRead runs once it's there.
	 */
	public static BufferedImage get(Credentials c, int size, final Runnable whenReady) {
		BufferedImage face = null;
		if (AccountManager.isLicensed(c) && c.local_uuid != null) {
			final String uuid = c.local_uuid.replace("-", "");
			face = faces.get(uuid);
			if (face == null) {
				face = fromDisk(uuid);
				if (face != null) faces.put(uuid, face);
			}
			if ((face == null || isStale(uuid)) && loading.add(uuid)) {
				new Thread("Head-" + uuid) {
					public void run() {
						BufferedImage fresh = download(uuid);
						loading.remove(uuid);
						if (fresh == null) return;
						faces.put(uuid, fresh);
						if (whenReady != null) SwingUtilities.invokeLater(whenReady);
					}
				}.start();
			}
		}
		if (face == null) face = steve();
		return scale(face, size);
	}

	private static File file(String uuid) {
		return new File(BC.get() + "launcher" + File.separator + "heads", uuid + ".png");
	}

	private static BufferedImage fromDisk(String uuid) {
		try {
			File f = file(uuid);
			return f.isFile() ? ImageIO.read(f) : null;
		} catch (Throwable t) {
			return null;
		}
	}

	/** Skins change: refresh a head once a day */
	private static boolean isStale(String uuid) {
		File f = file(uuid);
		return !f.isFile() || System.currentTimeMillis() - f.lastModified() > 24L * 3600 * 1000;
	}

	private static BufferedImage download(String uuid) {
		try {
			String url = LegacyProxy.textureUrl(uuid, "SKIN");
			if (url == null) return null;
			BufferedImage skin = ImageIO.read(new ByteArrayInputStream(LegacyProxy.download(url.replace("http://", "https://"))));
			if (skin == null || skin.getWidth() < 64) return null;

			BufferedImage face = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = face.createGraphics();
			g.drawImage(skin.getSubimage(8, 8, 8, 8), 0, 0, null);
			// hat layer; like the game, a fully opaque one counts as "no hat" (old skins filled it with a color)
			BufferedImage hat = skin.getSubimage(40, 8, 8, 8);
			if (hasTransparency(hat)) g.drawImage(hat, 0, 0, null);
			g.dispose();

			File f = file(uuid);
			f.getParentFile().mkdirs();
			ImageIO.write(face, "png", f);
			return face;
		} catch (Throwable t) {
			System.out.println("Couldn't load the head of " + uuid + ": " + t);
			return null;
		}
	}

	private static boolean hasTransparency(BufferedImage img) {
		for (int y = 0; y < img.getHeight(); y++)
			for (int x = 0; x < img.getWidth(); x++)
				if ((img.getRGB(x, y) >>> 24) < 128) return true;
		return false;
	}

	private static BufferedImage steve() {
		if (steve == null) {
			try {
				steve = ImageIO.read(Heads.class.getResourceAsStream("/icons/instance/steve.png"));
			} catch (Throwable t) {
				steve = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
			}
		}
		return steve;
	}

	private static BufferedImage scale(BufferedImage src, int size) {
		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(src, 0, 0, size, size, null);
		g.dispose();
		return out;
	}
}
