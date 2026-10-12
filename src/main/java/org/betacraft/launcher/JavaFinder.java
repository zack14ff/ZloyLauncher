package org.betacraft.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Looks for Java installations on this computer, like Prism Launcher does */
public class JavaFinder {

	public static class JavaInstall {
		public final String path;
		public final String version;
		public final String arch;
		public final String vendor;
		public final int major;
		/** Real folder of this Java, the same for symlinks and system stubs pointing at it */
		public final String home;

		JavaInstall(String path, String version, String arch, String vendor, String home) {
			this.path = path;
			this.version = version;
			this.arch = arch;
			this.vendor = vendor;
			this.major = majorOf(version);
			this.home = home;
		}

		/** System stubs like Common Files\Oracle\Java\javapath just forward to a real install */
		public boolean isStub() {
			String p = path.toLowerCase();
			return p.contains("common files") && p.contains("oracle");
		}

		public boolean is64Bit() {
			return arch != null && (arch.contains("64"));
		}

		/** Old Minecraft versions are happiest on Java 8 */
		public boolean isRecommended() {
			return major == 8 && is64Bit();
		}
	}

	private static List<JavaInstall> cache = null;

	/** Cached result of the last scan, scans if there was none */
	public static synchronized List<JavaInstall> getInstalls() {
		if (cache == null) cache = scan();
		return cache;
	}

	public static synchronized List<JavaInstall> rescan() {
		cache = scan();
		return cache;
	}

	/** Best Java 8 found, or null */
	public static String findJava8() {
		for (JavaInstall j : getInstalls()) {
			if (j.isRecommended()) return j.path;
		}
		for (JavaInstall j : getInstalls()) {
			if (j.major == 8) return j.path;
		}
		return null;
	}

	public static List<JavaInstall> scan() {
		// canonical path -> path as found, keeps the first (friendliest) spelling
		Map<String, String> candidates = new LinkedHashMap<String, String>();
		for (File f : candidateFiles()) {
			if (f == null || !f.isFile()) continue;
			try {
				String canonical = f.getCanonicalPath();
				if (!candidates.containsKey(canonical)) candidates.put(canonical, f.getAbsolutePath());
			} catch (Throwable t) {}
		}

		ExecutorService pool = Executors.newFixedThreadPool(4);
		List<Future<JavaInstall>> futures = new ArrayList<Future<JavaInstall>>();
		for (final String path : candidates.values()) {
			futures.add(pool.submit(new java.util.concurrent.Callable<JavaInstall>() {
				public JavaInstall call() {
					return probe(path);
				}
			}));
		}

		// one entry per real install: symlinked folders (Java\latest\...), JDK's jre\bin and system stubs merge
		Map<String, JavaInstall> byHome = new LinkedHashMap<String, JavaInstall>();
		for (Future<JavaInstall> f : futures) {
			try {
				JavaInstall j = f.get(15, TimeUnit.SECONDS);
				if (j == null) continue;
				String key = j.home + "|" + j.version + "|" + j.arch;
				JavaInstall known = byHome.get(key);
				if (known == null || (known.isStub() && !j.isStub())) byHome.put(key, j);
			} catch (Throwable t) {}
		}
		pool.shutdownNow();
		List<JavaInstall> found = new ArrayList<JavaInstall>(byHome.values());

		Collections.sort(found, new Comparator<JavaInstall>() {
			public int compare(JavaInstall a, JavaInstall b) {
				// recommended first, then Java 8, then newer versions first
				if (a.isRecommended() != b.isRecommended()) return a.isRecommended() ? -1 : 1;
				if ((a.major == 8) != (b.major == 8)) return a.major == 8 ? -1 : 1;
				if (a.major != b.major) return b.major - a.major;
				return a.path.compareToIgnoreCase(b.path);
			}
		});
		System.out.println("Found " + found.size() + " Java installation(s)");
		return found;
	}

	private static List<File> candidateFiles() {
		String exe = OS.isWindows() ? "java.exe" : "java";
		List<File> files = new ArrayList<File>();

		files.add(Launcher.javaRuntime);

		String javaHome = System.getenv("JAVA_HOME");
		if (javaHome != null) files.add(new File(javaHome, "bin" + File.separator + exe));

		String path = System.getenv("PATH");
		if (path != null) {
			for (String dir : path.split(File.pathSeparator)) {
				dir = dir.trim().replace("\"", "");
				if (dir.length() > 0) files.add(new File(dir, exe));
			}
		}

		List<File> roots = new ArrayList<File>();
		if (OS.isWindows()) {
			String[] programFiles = {System.getenv("ProgramFiles"), System.getenv("ProgramW6432"), System.getenv("ProgramFiles(x86)")};
			String[] vendors = {"Java", "Eclipse Adoptium", "Eclipse Foundation", "AdoptOpenJDK", "Zulu", "Amazon Corretto",
					"BellSoft", "Microsoft", "Semeru", "IBM", "RedHat", "Liberica"};
			for (String pf : programFiles) {
				if (pf == null) continue;
				for (String vendor : vendors) roots.add(new File(pf, vendor));
				// the official Minecraft launcher keeps its own Javas here
				roots.add(new File(pf, "Minecraft Launcher" + File.separator + "runtime"));
				roots.add(new File(pf, "Minecraft" + File.separator + "runtime"));
			}
			String appData = System.getenv("APPDATA");
			String localAppData = System.getenv("LOCALAPPDATA");
			if (appData != null) {
				roots.add(new File(appData, ".minecraft" + File.separator + "runtime"));
				roots.add(new File(appData, "PrismLauncher" + File.separator + "java"));
				roots.add(new File(appData, "MultiMC" + File.separator + "java"));
			}
			if (localAppData != null) {
				roots.add(new File(localAppData, "Packages" + File.separator + "Microsoft.4297127D64EC6_8wekyb3d8bbwe"
						+ File.separator + "LocalCache" + File.separator + "Local" + File.separator + "runtime"));
				roots.add(new File(localAppData, "Programs" + File.separator + "Eclipse Adoptium"));
			}
		} else if (OS.isMac()) {
			roots.add(new File("/Library/Java/JavaVirtualMachines"));
			roots.add(new File(System.getProperty("user.home"), "Library/Java/JavaVirtualMachines"));
			files.add(new File("/Library/Internet Plug-Ins/JavaAppletPlugin.plugin/Contents/Home/bin/java"));
		} else {
			roots.add(new File("/usr/lib/jvm"));
			roots.add(new File("/usr/lib64/jvm"));
			roots.add(new File("/usr/java"));
			roots.add(new File("/opt"));
			roots.add(new File(System.getProperty("user.home"), ".sdkman/candidates/java"));
			roots.add(new File(System.getProperty("user.home"), ".local/share/PrismLauncher/java"));
		}
		// Java 8u51 the launcher downloads for Intel graphics
		roots.add(new File(BC.get(), "java-8u51"));

		for (File root : roots) findJavaBelow(root, exe, 5, files);
		return files;
	}

	/** Collects bin/java(.exe) files below the folder, a few levels deep */
	private static void findJavaBelow(File dir, String exe, int depth, List<File> into) {
		if (depth < 0 || dir == null || !dir.isDirectory()) return;

		File candidate = new File(dir, "bin" + File.separator + exe);
		if (candidate.isFile()) {
			into.add(candidate);
			// a JDK 8 also has jre/bin/java, no need to look further inside
			return;
		}
		File[] children = dir.listFiles();
		if (children == null) return;
		for (File child : children) {
			if (child.isDirectory()) findJavaBelow(child, exe, depth - 1, into);
		}
	}

	/** Runs the Java to ask for its version, architecture and vendor */
	public static JavaInstall probe(String path) {
		Process p = null;
		try {
			ProcessBuilder pb = new ProcessBuilder(path, "-XshowSettings:properties", "-version");
			pb.redirectErrorStream(true);
			p = pb.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

			String version = null, arch = null, vendor = null, home = null, line;
			long deadline = System.currentTimeMillis() + 10000L;
			while ((line = reader.readLine()) != null && System.currentTimeMillis() < deadline) {
				String t = line.trim();
				if (t.startsWith("java.version =")) version = t.substring(t.indexOf('=') + 1).trim();
				else if (t.startsWith("os.arch =")) arch = t.substring(t.indexOf('=') + 1).trim();
				else if (t.startsWith("java.vendor =")) vendor = t.substring(t.indexOf('=') + 1).trim();
				else if (t.startsWith("java.home =")) home = t.substring(t.indexOf('=') + 1).trim();
				else if (version == null && t.contains(" version \"")) {
					// very old Javas don't know -XshowSettings, but still print this line
					version = t.substring(t.indexOf('"') + 1, t.lastIndexOf('"'));
				}
			}
			reader.close();
			if (version == null) return null;
			if (arch == null) arch = "?";
			if (vendor == null) vendor = "";
			return new JavaInstall(path, version, arch, vendor, realPath(home == null ? new File(path).getParentFile().getParent() : home));
		} catch (Throwable t) {
			return null;
		} finally {
			if (p != null) p.destroy();
		}
	}

	/** Resolves symlinks too (getCanonicalPath doesn't on Windows) */
	public static String realPath(String path) {
		if (path == null) return "";
		try {
			return java.nio.file.Paths.get(path).toRealPath().toString().toLowerCase();
		} catch (Throwable t) {
			return new File(path).getAbsolutePath().toLowerCase();
		}
	}

	/** Whether two java executables are the same install */
	public static boolean sameInstall(JavaInstall j, String javaPath) {
		if (javaPath == null) return false;
		if (realPath(j.path).equals(realPath(javaPath))) return true;
		File bin = new File(javaPath).getParentFile();
		if (bin == null || bin.getParentFile() == null) return false;
		String home = realPath(bin.getParent());
		// a JDK 8 reports its jre folder as home
		return home.equals(j.home) || (home + File.separator + "jre").equals(j.home);
	}

	public static int majorOf(String version) {
		if (version == null) return -1;
		try {
			String v = version;
			if (v.startsWith("1.")) v = v.substring(2);
			int end = 0;
			while (end < v.length() && Character.isDigit(v.charAt(end))) end++;
			return end == 0 ? -1 : Integer.parseInt(v.substring(0, end));
		} catch (Throwable t) {
			return -1;
		}
	}
}
