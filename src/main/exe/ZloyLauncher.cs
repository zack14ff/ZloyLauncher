// ZloyLauncher.exe - a small Windows starter with the launcher's icon.
// The launcher jar is embedded as a resource: it gets unpacked next to the user's data
// and started with Java (Java 8 preferred, since old Minecraft versions want it).
// Built by `gradlew exe` with the C# compiler that ships with Windows (.NET Framework 4).
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Windows.Forms;

[assembly: AssemblyTitle("ZloyLauncher")]
[assembly: AssemblyProduct("ZloyLauncher")]
[assembly: AssemblyDescription("Old Minecraft versions launcher")]
[assembly: AssemblyVersion("1.2.0.0")]
[assembly: AssemblyFileVersion("1.2.0.0")]

static class ZloyLauncherStarter
{
	const string JarResource = "ZloyLauncher.jar";

	[STAThread]
	static int Main(string[] args)
	{
		try
		{
			string jar = UnpackJar();
			string java = FindJava();
			if (java == null)
			{
				DialogResult res = MessageBox.Show(
					"Java не найдена. Для ZloyLauncher нужна Java 8.\nОткрыть страницу загрузки Java?\n\nJava wasn't found. ZloyLauncher needs Java 8.",
					"ZloyLauncher", MessageBoxButtons.YesNo, MessageBoxIcon.Warning);
				if (res == DialogResult.Yes) Process.Start("https://www.java.com/download/");
				return 1;
			}

			StringBuilder arguments = new StringBuilder();
			arguments.Append("-jar ").Append(Quote(jar));
			foreach (string arg in args) arguments.Append(' ').Append(Quote(arg));

			ProcessStartInfo info = new ProcessStartInfo(java, arguments.ToString());
			info.UseShellExecute = false;
			info.WorkingDirectory = Path.GetDirectoryName(jar);
			Process.Start(info);
			return 0;
		}
		catch (Exception e)
		{
			MessageBox.Show("Не удалось запустить ZloyLauncher:\n" + e.Message, "ZloyLauncher", MessageBoxButtons.OK, MessageBoxIcon.Error);
			return 1;
		}
	}

	/** Unpacks the embedded jar to %LOCALAPPDATA%\ZloyLauncher, only when it changed */
	static string UnpackJar()
	{
		string dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "ZloyLauncher");
		Directory.CreateDirectory(dir);
		string jar = Path.Combine(dir, "ZloyLauncher.jar");

		byte[] data;
		using (Stream s = Assembly.GetExecutingAssembly().GetManifestResourceStream(JarResource))
		{
			if (s == null) throw new Exception("the launcher jar is missing from the exe");
			data = new byte[s.Length];
			int read = 0;
			while (read < data.Length)
			{
				int n = s.Read(data, read, data.Length - read);
				if (n <= 0) break;
				read += n;
			}
		}

		if (!File.Exists(jar) || !SameBytes(File.ReadAllBytes(jar), data))
		{
			// write next to it first, so a running launcher never sees half a file
			string temp = jar + ".tmp";
			File.WriteAllBytes(temp, data);
			if (File.Exists(jar)) File.Delete(jar);
			File.Move(temp, jar);
		}
		return jar;
	}

	static bool SameBytes(byte[] a, byte[] b)
	{
		if (a.Length != b.Length) return false;
		using (SHA1 sha = SHA1.Create())
		{
			return Convert.ToBase64String(sha.ComputeHash(a)) == Convert.ToBase64String(sha.ComputeHash(b));
		}
	}

	static string FindJava()
	{
		List<string> java8 = new List<string>();
		List<string> other = new List<string>();

		string[] roots = {
			Environment.GetEnvironmentVariable("ProgramFiles"),
			Environment.GetEnvironmentVariable("ProgramW6432"),
			Environment.GetEnvironmentVariable("ProgramFiles(x86)")
		};
		string[] vendors = { "Java", "Eclipse Adoptium", "Eclipse Foundation", "AdoptOpenJDK", "Zulu", "Amazon Corretto", "BellSoft", "Microsoft" };

		foreach (string root in roots)
		{
			if (string.IsNullOrEmpty(root)) continue;
			foreach (string vendor in vendors)
			{
				string vendorDir = Path.Combine(root, vendor);
				if (!Directory.Exists(vendorDir)) continue;
				foreach (string jdk in Directory.GetDirectories(vendorDir))
				{
					string javaw = Path.Combine(jdk, "bin\\javaw.exe");
					if (!File.Exists(javaw)) continue;
					string name = Path.GetFileName(jdk).ToLowerInvariant();
					if (name.Contains("1.8") || name.Contains("-8") || name.Contains("8u") || name.StartsWith("jre8") || name.StartsWith("jdk8")) java8.Add(javaw);
					else other.Add(javaw);
				}
			}
		}

		// Java 8 that comes with the official Minecraft launcher
		string x86 = Environment.GetEnvironmentVariable("ProgramFiles(x86)");
		if (!string.IsNullOrEmpty(x86))
			AddIfExists(java8, Path.Combine(x86, "Minecraft Launcher\\runtime\\jre-legacy\\windows-x64\\jre-legacy\\bin\\javaw.exe"));
		AddIfExists(java8, Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
			"Packages\\Microsoft.4297127D64EC6_8wekyb3d8bbwe\\LocalCache\\Local\\runtime\\jre-legacy\\windows-x64\\jre-legacy\\bin\\javaw.exe"));

		if (java8.Count > 0) return java8[0];

		string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
		if (!string.IsNullOrEmpty(javaHome)) AddIfExists(other, Path.Combine(javaHome, "bin\\javaw.exe"), true);

		string path = Environment.GetEnvironmentVariable("PATH");
		if (path != null)
		{
			foreach (string dir in path.Split(';'))
			{
				try
				{
					if (dir.Trim().Length > 0) AddIfExists(other, Path.Combine(dir.Trim().Trim('"'), "javaw.exe"));
				}
				catch (ArgumentException) { }
			}
		}

		return other.Count > 0 ? other[0] : null;
	}

	static void AddIfExists(List<string> list, string file)
	{
		AddIfExists(list, file, false);
	}

	static void AddIfExists(List<string> list, string file, bool first)
	{
		if (!File.Exists(file) || list.Contains(file)) return;
		if (first) list.Insert(0, file);
		else list.Add(file);
	}

	static string Quote(string s)
	{
		return "\"" + s.Replace("\"", "\\\"") + "\"";
	}
}
