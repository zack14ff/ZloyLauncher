// ZloyLauncher.exe - a small Windows starter with the launcher's icon.
// The launcher jar is embedded as a resource: it gets unpacked next to the user's data
// and started with Java (Java 8 preferred, since old Minecraft versions want it).
// Built by `gradlew exe` with the C# compiler that ships with Windows (.NET Framework 4).
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.IO;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Windows.Forms;

[assembly: AssemblyTitle("ZloyLauncher")]
[assembly: AssemblyProduct("ZloyLauncher")]
[assembly: AssemblyDescription("Old Minecraft versions launcher")]
[assembly: AssemblyVersion("1.3.0.0")]
[assembly: AssemblyFileVersion("1.3.0.0")]

static class ZloyLauncherStarter
{
	const string JarResource = "ZloyLauncher.jar";
	/** Taskbar identity: windows, pins and shortcuts with this id belong together */
	public const string AppId = "zack14ff.ZloyLauncher";
	const string WindowTitlePrefix = "ZloyLauncher v";

	[STAThread]
	static int Main(string[] args)
	{
		try
		{
			string exe = Assembly.GetExecutingAssembly().Location;
			Taskbar.SetProcessAppId(AppId);

			// ZloyLauncher.exe --shortcut desktop|startmenu : used by the launcher's Folders menu
			if (args.Length >= 2 && args[0] == "--shortcut")
			{
				string where;
				if (args[1] == "startmenu") where = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), "ZloyLauncher.lnk");
				else if (args[1] == "desktop") where = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "ZloyLauncher.lnk");
				else if (args[1].EndsWith(".lnk", StringComparison.OrdinalIgnoreCase)) where = Path.GetFullPath(args[1]);
				else return 2;
				Shortcuts.Create(where, exe, AppId);
				return 0;
			}

			List<string> passOn = new List<string>(args);
			bool newInstance = passOn.Remove("--new-instance");

			// clicking a pinned icon while the launcher is open brings it up instead of starting a second one
			if (!newInstance && Taskbar.ActivateWindowStartingWith(WindowTitlePrefix)) return 0;

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
			// lets the launcher make shortcuts and know it was started from the exe
			arguments.Append("-Dzloylauncher.exe=").Append(Quote(exe)).Append(' ');
			// portable: a portable.txt next to the exe keeps all data in that folder
			string exeDir = Path.GetDirectoryName(exe);
			if (File.Exists(Path.Combine(exeDir, "portable.txt")) || Path.GetFileNameWithoutExtension(exe).ToLowerInvariant().Contains("portable"))
				arguments.Append("-Dzloylauncher.portableDir=").Append(Quote(exeDir)).Append(' ');
			arguments.Append("-jar ").Append(Quote(jar));
			foreach (string arg in passOn) arguments.Append(' ').Append(Quote(arg));

			ProcessStartInfo info = new ProcessStartInfo(java, arguments.ToString());
			info.UseShellExecute = false;
			info.WorkingDirectory = Path.GetDirectoryName(jar);
			Process launcher = Process.Start(info);

			// Java's windows belong to javaw.exe: give them our identity, so the taskbar groups them
			// under ZloyLauncher and "Pin to taskbar" pins this exe with its icon
			Taskbar.TagWindowsWhileRunning(launcher, AppId, exe, "ZloyLauncher");
			return 0;
		}
		catch (Exception e)
		{
			MessageBox.Show("Не удалось запустить ZloyLauncher:\n" + e.Message, "ZloyLauncher", MessageBoxButtons.OK, MessageBoxIcon.Error);
			return 1;
		}
	}

	/**
	 * Unpacks the embedded jar to %LOCALAPPDATA%\ZloyLauncher. Every build gets its own file name,
	 * so updating never touches a jar a running launcher still reads from.
	 */
	static string UnpackJar()
	{
		string dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "ZloyLauncher");
		Directory.CreateDirectory(dir);

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

		string hash;
		using (SHA1 sha = SHA1.Create())
		{
			hash = BitConverter.ToString(sha.ComputeHash(data)).Replace("-", "").Substring(0, 12).ToLowerInvariant();
		}
		string jar = Path.Combine(dir, "ZloyLauncher-" + hash + ".jar");

		if (!File.Exists(jar) || new FileInfo(jar).Length != data.Length)
		{
			string temp = jar + "." + Process.GetCurrentProcess().Id + ".tmp";
			File.WriteAllBytes(temp, data);
			if (File.Exists(jar)) File.Delete(jar);
			File.Move(temp, jar);
		}

		// old builds; one still running stays locked and is cleaned up next time
		foreach (string old in Directory.GetFiles(dir, "ZloyLauncher*.jar"))
		{
			if (string.Equals(old, jar, StringComparison.OrdinalIgnoreCase)) continue;
			try { File.Delete(old); } catch (Exception) { }
		}
		return jar;
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


/** Taskbar identity (AppUserModelID) for our own process and for the Java launcher's windows */
static class Taskbar
{
	[DllImport("shell32.dll")]
	static extern int SetCurrentProcessExplicitAppUserModelID([MarshalAs(UnmanagedType.LPWStr)] string appId);

	[DllImport("shell32.dll")]
	static extern int SHGetPropertyStoreForWindow(IntPtr hwnd, ref Guid iid, [Out, MarshalAs(UnmanagedType.Interface)] out IPropertyStore store);

	delegate bool EnumWindowsProc(IntPtr hwnd, IntPtr lParam);
	[DllImport("user32.dll")] static extern bool EnumWindows(EnumWindowsProc proc, IntPtr lParam);
	[DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint pid);
	[DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr hwnd);
	[DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetWindowText(IntPtr hwnd, StringBuilder text, int max);
	[DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr hwnd);
	[DllImport("user32.dll")] static extern bool ShowWindow(IntPtr hwnd, int cmd);
	[DllImport("user32.dll")] static extern bool IsIconic(IntPtr hwnd);

	public static void SetProcessAppId(string appId)
	{
		try { SetCurrentProcessExplicitAppUserModelID(appId); } catch (Exception) { }
	}

	public static bool ActivateWindowStartingWith(string prefix)
	{
		IntPtr found = IntPtr.Zero;
		EnumWindows(delegate(IntPtr hwnd, IntPtr l)
		{
			if (!IsWindowVisible(hwnd)) return true;
			StringBuilder sb = new StringBuilder(256);
			GetWindowText(hwnd, sb, sb.Capacity);
			if (sb.ToString().StartsWith(prefix)) { found = hwnd; return false; }
			return true;
		}, IntPtr.Zero);
		if (found == IntPtr.Zero) return false;
		if (IsIconic(found)) ShowWindow(found, 9); // SW_RESTORE
		SetForegroundWindow(found);
		return true;
	}

	/** Tags every top-level window of the process until it exits */
	public static void TagWindowsWhileRunning(Process p, string appId, string exe, string name)
	{
		HashSet<IntPtr> tagged = new HashSet<IntPtr>();
		uint pid = (uint) p.Id;
		while (!p.HasExited)
		{
			EnumWindows(delegate(IntPtr hwnd, IntPtr l)
			{
				uint owner;
				GetWindowThreadProcessId(hwnd, out owner);
				if (owner == pid && IsWindowVisible(hwnd) && !tagged.Contains(hwnd))
				{
					if (TagWindow(hwnd, appId, exe, name)) tagged.Add(hwnd);
				}
				return true;
			}, IntPtr.Zero);
			p.WaitForExit(700);
		}
	}

	public static bool TagWindow(IntPtr hwnd, string appId, string exe, string name)
	{
		try
		{
			Guid iid = typeof(IPropertyStore).GUID;
			IPropertyStore store;
			if (SHGetPropertyStoreForWindow(hwnd, ref iid, out store) != 0 || store == null) return false;
			try
			{
				// relaunch details first, the id last: pinning reads them all
				PropKeys.Set(store, PropKeys.RelaunchCommand, "\"" + exe + "\"");
				PropKeys.Set(store, PropKeys.RelaunchDisplayName, name);
				PropKeys.Set(store, PropKeys.RelaunchIcon, exe + ",0");
				PropKeys.Set(store, PropKeys.AppId, appId);
				store.Commit();
			}
			finally
			{
				Marshal.ReleaseComObject(store);
			}
			return true;
		}
		catch (Exception)
		{
			return false;
		}
	}
}

/** .lnk files with our icon and taskbar identity */
static class Shortcuts
{
	[ComImport, Guid("00021401-0000-0000-C000-000000000046")]
	class ShellLink { }

	[ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("000214F9-0000-0000-C000-000000000046")]
	interface IShellLinkW
	{
		void GetPath([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder file, int max, IntPtr fd, uint flags);
		void GetIDList(out IntPtr pidl);
		void SetIDList(IntPtr pidl);
		void GetDescription([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder name, int max);
		void SetDescription([MarshalAs(UnmanagedType.LPWStr)] string name);
		void GetWorkingDirectory([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder dir, int max);
		void SetWorkingDirectory([MarshalAs(UnmanagedType.LPWStr)] string dir);
		void GetArguments([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder args, int max);
		void SetArguments([MarshalAs(UnmanagedType.LPWStr)] string args);
		void GetHotkey(out short hotkey);
		void SetHotkey(short hotkey);
		void GetShowCmd(out int cmd);
		void SetShowCmd(int cmd);
		void GetIconLocation([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder path, int max, out int index);
		void SetIconLocation([MarshalAs(UnmanagedType.LPWStr)] string path, int index);
		void SetRelativePath([MarshalAs(UnmanagedType.LPWStr)] string path, uint reserved);
		void Resolve(IntPtr hwnd, uint flags);
		void SetPath([MarshalAs(UnmanagedType.LPWStr)] string file);
	}

	[ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("0000010b-0000-0000-C000-000000000046")]
	interface IPersistFile
	{
		void GetClassID(out Guid clsid);
		[PreserveSig] int IsDirty();
		void Load([MarshalAs(UnmanagedType.LPWStr)] string file, uint mode);
		void Save([MarshalAs(UnmanagedType.LPWStr)] string file, bool remember);
		void SaveCompleted([MarshalAs(UnmanagedType.LPWStr)] string file);
		void GetCurFile([MarshalAs(UnmanagedType.LPWStr)] out string file);
	}

	public static void Create(string lnk, string exe, string appId)
	{
		IShellLinkW link = (IShellLinkW) new ShellLink();
		link.SetPath(exe);
		link.SetWorkingDirectory(Path.GetDirectoryName(exe));
		link.SetIconLocation(exe, 0);
		link.SetDescription("ZloyLauncher - old Minecraft versions");
		// same id as the windows: the shortcut, a pin and the running launcher are one app for the taskbar
		IPropertyStore store = (IPropertyStore) link;
		PropKeys.Set(store, PropKeys.AppId, appId);
		store.Commit();
		Directory.CreateDirectory(Path.GetDirectoryName(lnk));
		((IPersistFile) link).Save(lnk, true);
		Marshal.ReleaseComObject(link);
	}
}

[ComImport, Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IPropertyStore
{
	void GetCount(out uint count);
	void GetAt(uint index, out PropertyKey key);
	void GetValue(ref PropertyKey key, out PropVariant value);
	void SetValue(ref PropertyKey key, ref PropVariant value);
	void Commit();
}

[StructLayout(LayoutKind.Sequential, Pack = 4)]
struct PropertyKey
{
	public Guid fmtid;
	public uint pid;

	public PropertyKey(Guid fmtid, uint pid)
	{
		this.fmtid = fmtid;
		this.pid = pid;
	}
}

/** Just enough of PROPVARIANT for one string (VT_LPWSTR) */
[StructLayout(LayoutKind.Explicit)]
struct PropVariant
{
	[FieldOffset(0)] public ushort vt;
	[FieldOffset(8)] public IntPtr pointer;
	[FieldOffset(16)] public IntPtr padding; // PROPVARIANT is 24 bytes on 64-bit

	public static PropVariant FromString(string s)
	{
		PropVariant v = new PropVariant();
		v.vt = 31; // VT_LPWSTR
		v.pointer = Marshal.StringToCoTaskMemUni(s);
		return v;
	}
}

static class PropKeys
{
	static readonly Guid AppUserModel = new Guid("9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3");
	public static PropertyKey RelaunchCommand = new PropertyKey(AppUserModel, 2);
	public static PropertyKey RelaunchIcon = new PropertyKey(AppUserModel, 3);
	public static PropertyKey RelaunchDisplayName = new PropertyKey(AppUserModel, 4);
	public static PropertyKey AppId = new PropertyKey(AppUserModel, 5);

	public static void Set(IPropertyStore store, PropertyKey key, string value)
	{
		PropVariant v = PropVariant.FromString(value);
		try
		{
			store.SetValue(ref key, ref v);
		}
		finally
		{
			Marshal.FreeCoTaskMem(v.pointer);
		}
	}
}
