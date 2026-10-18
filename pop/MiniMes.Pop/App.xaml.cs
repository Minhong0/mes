using System.Windows;
using MiniMes.Pop.Api;
using MiniMes.Pop.Storage;
using MiniMes.Pop.ViewModels;
using MiniMes.Pop.Views;

namespace MiniMes.Pop;

/// <summary>
/// 시작할 때 서버 주소를 정하고, API·대화 상자·ViewModel을 만들어 창에 연결한다.
/// 서버 주소: 환경 변수 MES_URL (없으면 http://localhost:8080/)
/// 보내지 못한 실적: %LOCALAPPDATA%\MiniMes.Pop\pending\{사번}.json
/// </summary>
public partial class App : Application
{
    // 현장 네트워크가 느려도 화면이 오래 멈추지 않게 짧게 잡는다. 넘으면 "응답 없음"으로 보고 다시 보내게 한다.
    private static readonly TimeSpan RequestTimeout = TimeSpan.FromSeconds(5);

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        var serverUrl = Environment.GetEnvironmentVariable("MES_URL") ?? "http://localhost:8080/";
        var api = new MesApiClient(new Uri(serverUrl), RequestTimeout);
        var window = new MainWindow { DataContext = new MainViewModel(api, new WpfDialogService(), new FilePendingReportStore(FilePendingReportStore.DefaultDirectory)) };
        window.Show();
    }
}
