using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text;
using System.Windows.Forms;

internal static class FlippingTablesLauncher
{
    private static readonly object LogLock = new object();
    private static string logPath;
    private static string apiToken;

    [STAThread]
    private static int Main(string[] args)
    {
        bool checkOnly = args.Contains("--check");
        try
        {
            string root = AppDomain.CurrentDomain.BaseDirectory;
            string logDirectory = Path.Combine(root, "logs");
            Directory.CreateDirectory(logDirectory);
            logPath = Path.Combine(logDirectory, "launcher-" + DateTime.Now.ToString("yyyyMMdd-HHmmss-fff") + ".log");
            string accessFile = ParseArguments(args);
            string versionText = File.ReadAllText(Path.Combine(root, "client-version.txt")).Trim();
            Version version;
            if (!Version.TryParse(versionText, out version))
            {
                throw new InvalidOperationException("The installed client version is invalid. Reinstall the personal client.");
            }
            string jarPath = Path.Combine(root, "build", "libs", "flippingtables-" + versionText + "-all.jar");
            string runtime = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "RuneLite", "jre", "bin");
            string javaPath = Path.Combine(runtime, checkOnly ? "java.exe" : "javaw.exe");
            if (!File.Exists(jarPath))
            {
                throw new FileNotFoundException("The personal client JAR is missing. Reinstall the personal client.", jarPath);
            }
            if (!File.Exists(javaPath))
            {
                throw new FileNotFoundException("Install the official RuneLite Windows launcher to provide its Java runtime.", javaPath);
            }
            if (accessFile != null)
            {
                apiToken = ReadToken(accessFile);
            }
            ProcessStartInfo start = new ProcessStartInfo(javaPath,
                "-ea -jar " + Quote(jarPath) + (checkOnly ? " --help" : " --developer-mode"));
            start.WorkingDirectory = root;
            start.UseShellExecute = false;
            start.CreateNoWindow = true;
            start.RedirectStandardOutput = true;
            start.RedirectStandardError = true;
            if (apiToken != null)
            {
                start.EnvironmentVariables["FLIPPING_TABLES_API_TOKEN"] = apiToken;
            }
            WriteLog("Starting Flipping Tables " + versionText + (checkOnly ? " launcher check." : "."));
            using (Process process = new Process())
            {
                process.StartInfo = start;
                process.OutputDataReceived += (sender, line) => WriteLog(line.Data);
                process.ErrorDataReceived += (sender, line) => WriteLog(line.Data);
                process.Start();
                process.BeginOutputReadLine();
                process.BeginErrorReadLine();
                if (checkOnly && !process.WaitForExit(30000))
                {
                    process.Kill();
                    throw new TimeoutException("The RuneLite launcher check did not finish within 30 seconds.");
                }
                process.WaitForExit();
                WriteLog("Client exited with code " + process.ExitCode + ".");
                if (process.ExitCode != 0 && !checkOnly)
                {
                    ShowFailure("RuneLite exited unexpectedly (code " + process.ExitCode + ").");
                }
                return process.ExitCode;
            }
        }
        catch (Exception error)
        {
            WriteLog(error.GetType().Name + ": " + error.Message);
            if (!checkOnly)
            {
                ShowFailure(error.Message);
            }
            return 1;
        }
    }

    private static string ParseArguments(string[] args)
    {
        string accessFile = null;
        for (int index = 0; index < args.Length; index++)
        {
            if (args[index] == "--check")
            {
                continue;
            }
            if (args[index] == "--api-access-file" && index + 1 < args.Length && accessFile == null)
            {
                accessFile = args[++index];
                continue;
            }
            throw new ArgumentException("Unrecognized launcher argument. Reinstall the Start-menu shortcut.");
        }
        return accessFile;
    }

    private static string ReadToken(string path)
    {
        string[] values = File.ReadAllLines(path)
            .Where(line => line.StartsWith("API_BEARER_TOKEN=", StringComparison.Ordinal))
            .Select(line => line.Substring("API_BEARER_TOKEN=".Length).Trim()).ToArray();
        if (values.Length != 1 || String.IsNullOrWhiteSpace(values[0]))
        {
            throw new InvalidOperationException("The API access file must contain exactly one non-empty API_BEARER_TOKEN entry.");
        }
        return values[0];
    }

    private static string Quote(string value)
    {
        return "\"" + value + "\"";
    }

    private static void WriteLog(string message)
    {
        if (String.IsNullOrEmpty(message) || logPath == null)
        {
            return;
        }
        if (!String.IsNullOrEmpty(apiToken))
        {
            message = message.Replace(apiToken, "[redacted]");
        }
        try
        {
            lock (LogLock)
            {
                if (!File.Exists(logPath) || new FileInfo(logPath).Length < 1024 * 1024)
                {
                    File.AppendAllText(logPath, message + Environment.NewLine, Encoding.UTF8);
                }
            }
        }
        catch (IOException)
        {
        }
        catch (UnauthorizedAccessException)
        {
        }
    }

    private static void ShowFailure(string message)
    {
        if (!String.IsNullOrEmpty(apiToken))
        {
            message = message.Replace(apiToken, "[redacted]");
        }
        MessageBox.Show(message + "\n\nStartup details: " + (logPath ?? "Unable to create a startup log."),
            "Flipping Tables could not start", MessageBoxButtons.OK, MessageBoxIcon.Error);
    }
}
