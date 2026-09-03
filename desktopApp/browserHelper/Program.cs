using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;
using System.Text.Json;

namespace JieXiLogin;

internal static class Program
{
    [STAThread]
    private static void Main(string[] args)
    {
        try
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            var options = args
                .Select((value, index) => (value, index))
                .Where(item => item.value.StartsWith("--") && item.index + 1 < args.Length)
                .ToDictionary(item => item.value, item => args[item.index + 1], StringComparer.OrdinalIgnoreCase);
            if (!options.TryGetValue("--url", out var url) ||
                !options.TryGetValue("--output", out var output) ||
                !options.TryGetValue("--profile", out var profile) ||
                !options.TryGetValue("--allowed-hosts", out var allowedHosts))
            {
                File.WriteAllText(Path.Combine(Path.GetTempPath(), "jiexi-embedded-login-args.txt"), string.Join("|", args));
                return;
            }
            Application.Run(new LoginForm(url, output, profile, allowedHosts));
        }
        catch (Exception ex)
        {
            File.WriteAllText(Path.Combine(Path.GetTempPath(), "jiexi-embedded-login-error.txt"), ex.ToString());
            MessageBox.Show(ex.Message, "解析内置登录", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }
}

internal sealed class LoginForm : Form
{
    private readonly string loginUrl;
    private readonly string outputPath;
    private readonly string profilePath;
    private readonly HashSet<string> allowedHosts;
    private readonly WebView2 browser = new() { Dock = DockStyle.Fill };
    private readonly Label status = new()
    {
        AutoSize = true,
        Text = "正在启动安全登录页…",
        ForeColor = Color.FromArgb(119, 107, 96),
        Font = new Font("Microsoft YaHei UI", 9F)
    };
    private readonly Button finish = new()
    {
        Text = "登录完成并导入",
        AutoSize = true,
        Enabled = false,
        BackColor = Color.FromArgb(199, 101, 39),
        ForeColor = Color.White,
        FlatStyle = FlatStyle.Flat,
        Font = new Font("Microsoft YaHei UI", 9F, FontStyle.Bold),
        Padding = new Padding(12, 5, 12, 5)
    };

    internal LoginForm(string loginUrl, string outputPath, string profilePath, string allowedHosts)
    {
        this.loginUrl = loginUrl;
        this.outputPath = outputPath;
        this.profilePath = profilePath;
        this.allowedHosts = allowedHosts.Split(new[] { ',' }, StringSplitOptions.RemoveEmptyEntries)
            .Select(host => host.Trim().TrimStart('.').ToLowerInvariant())
            .ToHashSet(StringComparer.OrdinalIgnoreCase);
        Text = "解析 · 云盘登录";
        Width = 1040;
        Height = 760;
        MinimumSize = new Size(760, 560);
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = Color.FromArgb(251, 248, 242);
        Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);

        var top = new TableLayoutPanel
        {
            Dock = DockStyle.Top,
            Height = 62,
            BackColor = Color.FromArgb(255, 253, 249),
            Padding = new Padding(16, 9, 16, 9),
            ColumnCount = 4
        };
        top.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 54));
        top.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        top.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));
        top.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));
        var back = ToolbarButton("←", (_, _) => { if (browser.CanGoBack) browser.GoBack(); });
        var reload = ToolbarButton("刷新", (_, _) => browser.Reload());
        var text = new Panel { Dock = DockStyle.Fill };
        var title = new Label
        {
            AutoSize = true,
            Text = "直接登录云盘",
            Font = new Font("Microsoft YaHei UI", 11F, FontStyle.Bold),
            ForeColor = Color.FromArgb(48, 40, 32),
            Location = new Point(0, 0)
        };
        status.Location = new Point(0, 25);
        text.Controls.Add(title);
        text.Controls.Add(status);
        finish.FlatAppearance.BorderSize = 0;
        finish.Click += ImportCookies;
        top.Controls.Add(back, 0, 0);
        top.Controls.Add(text, 1, 0);
        top.Controls.Add(reload, 2, 0);
        top.Controls.Add(finish, 3, 0);
        Controls.Add(browser);
        Controls.Add(top);
        Shown += async (_, _) => await InitializeBrowser();
    }

    private static Button ToolbarButton(string text, EventHandler click)
    {
        var button = new Button
        {
            Text = text,
            AutoSize = true,
            FlatStyle = FlatStyle.Flat,
            BackColor = Color.FromArgb(255, 253, 249),
            ForeColor = Color.FromArgb(90, 76, 64),
            Font = new Font("Microsoft YaHei UI", 9F),
            Padding = new Padding(7, 4, 7, 4),
            Margin = new Padding(4)
        };
        button.FlatAppearance.BorderColor = Color.FromArgb(233, 222, 208);
        button.Click += click;
        return button;
    }

    private async Task InitializeBrowser()
    {
        try
        {
            Directory.CreateDirectory(profilePath);
            var environment = await CoreWebView2Environment.CreateAsync(null, profilePath);
            await browser.EnsureCoreWebView2Async(environment);
            browser.CoreWebView2.Settings.AreDevToolsEnabled = false;
            browser.CoreWebView2.Settings.IsStatusBarEnabled = false;
            browser.CoreWebView2.Settings.AreDefaultContextMenusEnabled = true;
            browser.CoreWebView2.NavigationCompleted += (_, e) =>
            {
                status.Text = e.IsSuccess ? "请在下方网页完成登录，然后点击右上角按钮" : "网页加载失败，可点击刷新重试";
            };
            browser.CoreWebView2.NavigationStarting += (_, e) =>
            {
                if (IsAllowed(e.Uri)) return;
                e.Cancel = true;
                if (Uri.TryCreate(e.Uri, UriKind.Absolute, out var uri) && uri.Scheme == Uri.UriSchemeHttps)
                {
                    System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(uri.AbsoluteUri) { UseShellExecute = true });
                    status.Text = "非网盘页面已在外部浏览器打开";
                }
            };
            browser.CoreWebView2.Navigate(loginUrl);
            finish.Enabled = true;
        }
        catch (Exception ex)
        {
            status.Text = "内置登录启动失败：" + ex.Message;
            MessageBox.Show(this, status.Text, "解析", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    private bool IsAllowed(string value)
    {
        if (!Uri.TryCreate(value, UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttps) return false;
        var host = uri.Host.TrimEnd('.').ToLowerInvariant();
        return allowedHosts.Any(domain => host == domain || host.EndsWith("." + domain, StringComparison.OrdinalIgnoreCase));
    }

    private async void ImportCookies(object? sender, EventArgs e)
    {
        if (browser.CoreWebView2 is null) return;
        finish.Enabled = false;
        status.Text = "正在读取当前网盘授权…";
        try
        {
            var cookies = await browser.CoreWebView2.CookieManager.GetCookiesAsync(null);
            var payload = cookies.Select(cookie => new
            {
                name = cookie.Name,
                value = cookie.Value,
                domain = cookie.Domain,
                path = cookie.Path,
                expires = cookie.IsSession ? 0 : new DateTimeOffset(cookie.Expires.ToUniversalTime()).ToUnixTimeSeconds(),
                secure = cookie.IsSecure,
                httpOnly = cookie.IsHttpOnly
            }).ToArray();
            Directory.CreateDirectory(Path.GetDirectoryName(outputPath)!);
            File.WriteAllText(outputPath, JsonSerializer.Serialize(payload));
            DialogResult = DialogResult.OK;
            Close();
        }
        catch (Exception ex)
        {
            finish.Enabled = true;
            status.Text = "导入失败：" + ex.Message;
            MessageBox.Show(this, status.Text, "解析", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }
}
